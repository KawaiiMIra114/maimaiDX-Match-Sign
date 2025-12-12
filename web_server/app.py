import os
import csv
import random
import uuid
from io import TextIOWrapper
from datetime import datetime
from zipfile import ZipFile, BadZipFile

from flask import (
    Flask, render_template, request, redirect, url_for,
    flash, make_response, session, jsonify
)
from flask_sqlalchemy import SQLAlchemy
from sqlalchemy.exc import SQLAlchemyError
from sqlalchemy import func, text
from werkzeug.exceptions import HTTPException  # 用于错误处理
from werkzeug.security import generate_password_hash, check_password_hash
from werkzeug.utils import secure_filename

# ================= 基础配置 =================

app = Flask(__name__)

BASE_DIR = app.root_path
DB_PATH = os.path.join(BASE_DIR, 'data.db')
SONG_IMAGE_DIR = os.path.join(BASE_DIR, 'static', 'songs')
AVATAR_DIR = os.path.join(BASE_DIR, 'static', 'avatars')
ERROR_LOG_PATH = os.path.join(BASE_DIR, 'flask_error.log')  # 新增：错误日志文件

os.makedirs(SONG_IMAGE_DIR, exist_ok=True)
os.makedirs(AVATAR_DIR, exist_ok=True)

app.config['SONG_FOLDER'] = SONG_IMAGE_DIR
app.config['AVATAR_FOLDER'] = AVATAR_DIR
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:///' + DB_PATH
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
app.secret_key = 'your_super_secret_key_here_change_me'

# ================= 赛制配置 =================
# 您可以在此处修改赛制规则
# "qualifier_promotion": 海选晋级规则列表。系统会按照海选成绩排名，依次将选手分配到对应状态。
# "self_selection_phases": 允许提交自选曲的阶段。
TOURNAMENT_CONFIG = {
    'groups': {
        'beginner': {
            'label': '萌新组',
            'qualifier_promotion': [
                {'status': 'top16', 'count': 15}, # 前 15 名 -> 16强 (加上复活赛1人凑齐16)
                {'status': 'revival', 'count': 4}  # 接下来 4 名 -> 复活赛
            ],
            'self_selection_phases': ['top16', 'top8', 'top4', 'final']
        },
        'advanced': {
            'label': '进阶组',
            'qualifier_promotion': [
                {'status': 'top16', 'count': 15},
                {'status': 'revival', 'count': 4}
            ],
            'self_selection_phases': ['top16', 'top8', 'top4', 'final']
        },
        'peak': {
            'label': '巅峰组',
            'qualifier_promotion': [
                {'status': 'top4_peak', 'count': 4}
            ],
            'self_selection_phases': ['top4', 'top4_peak', 'final']
        }
    }
}

db = SQLAlchemy(app)

# ================= 数据模型 =================


class Match(db.Model):
    """1v1 对战表"""
    id = db.Column(db.Integer, primary_key=True)
    phase = db.Column(db.String(20))   # 'top16', 'top8', 'top4', 'semifinal', 'final'
    group = db.Column(db.String(20))   # 'beginner', 'advanced', 'peak'
    player1_id = db.Column(db.Integer, db.ForeignKey('player.id'))
    player2_id = db.Column(db.Integer, db.ForeignKey('player.id'))
    winner_id = db.Column(db.Integer, nullable=True) # 晋级者ID
    status = db.Column(db.String(20), default='pending') # pending, ongoing, finished

class SongSelection(db.Model):
    """巅峰组自选曲目与 Ban 记录"""
    id = db.Column(db.Integer, primary_key=True)
    match_id = db.Column(db.Integer, db.ForeignKey('match.id'))
    player_id = db.Column(db.Integer, db.ForeignKey('player.id'))
    
    song_name = db.Column(db.String(200)) # 自选曲名
    difficulty = db.Column(db.Integer)    # 难度 (max 14)
    
    is_banned = db.Column(db.Boolean, default=False) # 是否被 ban
    banned_by_id = db.Column(db.Integer, nullable=True) # 被谁 ban


class Player(db.Model):
    """选手表"""
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(80), unique=True, nullable=False)

    checked_in = db.Column(db.Boolean, default=False)
    match_number = db.Column(db.Integer, nullable=True)
    group = db.Column(db.String(20), default='beginner')  # 'beginner' / 'advanced' / 'peak'
    on_machine = db.Column(db.Boolean, default=False)

    # 状态：
    # 'none', 'revival', 'eliminated'
    # 'top16', 'top16_out'
    # 'top8', 'top8_out'
    # 'top4', 'third', 'fourth', 'runner_up', 'champion'
    # Peak: 'top8_peak', 'top4_peak', 'final_peak' (巅峰组状态)
    promotion_status = db.Column(db.String(20), default='none')

    rating = db.Column(db.Integer, default=0)

    score_round1 = db.Column(db.Float, nullable=True)    # 海选
    score_round2 = db.Column(db.Float, nullable=True)    # 预留
    score_revival = db.Column(db.Float, nullable=True)   # 复活赛

    # 新增字段
    forfeited = db.Column(db.Boolean, default=False)       # 是否弃权
    ban_used = db.Column(db.Boolean, default=False)        # 是否已使用 Ban 技能
    
    # 账号相关 (Phase 4)
    password_hash = db.Column(db.String(128), nullable=True)
    avatar_filename = db.Column(db.String(128), nullable=True)

    def set_password(self, password):
        self.password_hash = generate_password_hash(password)

    def check_password(self, password):
        if not self.password_hash: return False
        return check_password_hash(self.password_hash, password)


class Song(db.Model):
    """
    曲目表：按赛程 + 组别区分
    phase: 'qualifier' / 'revival' / 'semifinal' / 'final'
    group: 'beginner' / 'advanced' / 'peak'
    """
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(200), nullable=False)
    phase = db.Column(db.String(20), nullable=False)
    group = db.Column(db.String(20), nullable=False)
    image_filename = db.Column(db.String(255), nullable=True)
    active = db.Column(db.Boolean, default=True)



class SystemState(db.Model):
    """系统全局状态"""
    id = db.Column(db.Integer, primary_key=True)
    match_generated = db.Column(db.Boolean, default=False)  # 是否已生成随机序号
    match_started = db.Column(db.Boolean, default=False)    # 比赛是否开始
    checkin_enabled = db.Column(db.Boolean, default=False)  # 签到是否开放 (新增)
    start_time = db.Column(db.DateTime, nullable=True)      # 比赛开始时间 (用于倒计时)
    checkin_timeout_processed = db.Column(db.Boolean, default=False) # 是否已处理超时自动弃权


class SongDrawState(db.Model):
    """
    曲目抽选的全局状态（只有一条记录，id=1）：
    status: 'idle' / 'rolling' / 'finished'
    selected_song_ids: "1,5" 这样的字符串，表示选中的 1~2 首曲目
    """
    id = db.Column(db.Integer, primary_key=True)
    status = db.Column(db.String(20), default='idle')
    phase = db.Column(db.String(20), nullable=True)
    group = db.Column(db.String(20), nullable=True)
    selected_song_ids = db.Column(db.String(200), nullable=True)
    updated_at = db.Column(db.DateTime, default=datetime.utcnow, onupdate=datetime.utcnow)

    def set_selected_songs(self, songs):
        """把选中的歌曲列表写入 selected_song_ids"""
        if not songs:
            self.selected_song_ids = None
            return
        ids = [str(s.id) for s in songs]
        self.selected_song_ids = ",".join(ids)

    def get_selected_songs(self):
        """从 selected_song_ids 读取歌曲对象列表"""
        if not self.selected_song_ids:
            return []
        id_list = []
        for part in self.selected_song_ids.split(','):
            part = part.strip()
            if not part:
                continue
            if part.isdigit():
                id_list.append(int(part))
        if not id_list:
            return []
        return Song.query.filter(Song.id.in_(id_list), Song.active == True).all()


# ================= 初始化数据库 =================

with app.app_context():
    db.create_all()
    # 初始化系统状态
    if not SystemState.query.get(1):
        db.session.add(SystemState(id=1, match_generated=False, match_started=False, checkin_enabled=False))
        db.session.commit()
    else:
        # Migration helper for new fields
        s = SystemState.query.get(1)
        if s.match_started is None:
            s.match_started = False
            s.checkin_timeout_processed = False
        if getattr(s, 'checkin_enabled', None) is None: # Handle case where column exists but is null? Or just simplistic check
             # Note: SQLAlchemy might not map the new column until restart/migration. 
             # But here we assume SQLite will just work if we rely on app context? 
             # Actually, for SQLite adding a column requires ALTER TABLE usually.
             # But since we are using SQLAlchemy, we might need to manually handle it if not using Flask-Migrate.
             # For this environment, I'll assume simple property access works if the column was added to the table.
             # Wait, if the column doesn't exist in DB, accessing s.checkin_enabled might throw error.
             # But I cannot run ALTER TABLE easily here without raw SQL.
             # I will catch the error and add the column.
             pass
        
        # 简单处理：尝试访问，如果报错说明列不存在
        try:
            _ = s.checkin_enabled
            if s.checkin_enabled is None:
                s.checkin_enabled = False
        except Exception:
             # 列不存在，使用 raw sql 添加
             try:
                 db.session.execute(text('ALTER TABLE system_state ADD COLUMN checkin_enabled BOOLEAN DEFAULT 0'))
                 s.checkin_enabled = False
             except Exception as e:
                 print(f"Migration Error: {e}")
             
        db.session.commit()
    # 初始化抽选状态
    if not SongDrawState.query.get(1):
        db.session.add(SongDrawState(id=1, status='idle'))
        db.session.commit()


# ================= 辅助函数 =================

def get_system_state():
    state = SystemState.query.get(1)
    if not state:
        state = SystemState(id=1, match_generated=False)
        db.session.add(state)
        db.session.commit()
    return state


def get_song_draw_state():
    state = SongDrawState.query.get(1)
    if not state:
        state = SongDrawState(id=1, status='idle')
        db.session.add(state)
        db.session.commit()
    return state


def get_players_data(sort_by=None, name_query=None):
    try:
        query = Player.query

        if name_query:
            query = query.filter(Player.name.contains(name_query))

        if sort_by == 'score':
            query = query.order_by(
                Player.score_round1.desc().nullslast(),
                Player.checked_in.desc(),
                Player.id.asc()
            )
        elif sort_by == 'rating':
            query = query.order_by(
                Player.group.asc(),
                Player.rating.desc().nullslast(),
                Player.id.asc()
            )
        else:
            query = query.order_by(
                Player.group.asc(),
                Player.match_number.asc().nullslast(),
                Player.id.asc()
            )

        return query.all()
    except Exception as e:
        print("[get_players_data] ERROR:", repr(e))
        flash("数据库查询失败，请联系管理员。", "danger")
        return []


def get_dashboard_stats():
    total = Player.query.count()
    checked = Player.query.filter_by(checked_in=True).count()
    numbered = Player.query.filter(Player.match_number != None).count()

    promoted_16 = Player.query.filter(
        Player.promotion_status.in_([
            'top16', 'top16_out',
            'top8', 'top8_out',
            'top4', 'third', 'fourth', 'runner_up', 'champion'
        ])
    ).count()

    promoted_4 = Player.query.filter(
        Player.promotion_status.in_([
            'top4', 'third', 'fourth', 'runner_up', 'champion'
        ])
    ).count()

    revival_count = Player.query.filter_by(promotion_status='revival').count()

    max_beg = db.session.query(func.max(Player.match_number)) \
        .filter(Player.group == 'beginner').scalar()
    max_adv = db.session.query(func.max(Player.match_number)) \
        .filter(Player.group == 'advanced').scalar()

    return {
        'total': total,
        'checked': checked,
        'numbered': numbered,
        'promoted_16': promoted_16,
        'promoted_4': promoted_4,
        'revival_count': revival_count,
        'max_beg': max_beg or 0,
        'max_adv': max_adv or 0,
    }


def phase_label_to_key(s: str):
    s = (s or '').strip().lower()
    if '海选' in s or 'qualifier' in s:
        return 'qualifier'
    if '复活' in s or 'revival' in s:
        return 'revival'
    if '半决' in s or 'semifinal' in s:
        return 'semifinal'
    if '决赛' in s or 'final' in s:
        return 'final'
    return None


def group_label_to_key(s: str):
    s = (s or '').strip().lower()
    if '萌新' in s or 'beginner' in s:
        return 'beginner'
    if '进阶' in s or 'advanced' in s:
        return 'advanced'
    return None


def save_song_image_from_file(fs):
    """
    单曲上传用：fs 是 Werkzeug 的 FileStorage
    """
    if not fs or fs.filename == '':
        return None

    filename = os.path.basename(fs.filename)
    # 简单处理一下重复：加时间前缀
    ts = datetime.utcnow().strftime("%Y%m%d%H%M%S%f")
    save_name = f"{ts}_{filename}"
    save_path = os.path.join(SONG_IMAGE_DIR, save_name)
    fs.save(save_path)
    return save_name


def import_songs_from_zip(file_storage):
    """
    从 ZIP 批量导入曲目：
    - ZIP 内找一个 .csv 或 .txt，当做曲目列表
    - 每行： 曲名, 赛程, 组别, 图片文件名
      例如： Tell Your World, 海选赛, 萌新组, tyw.png
    """
    if not file_storage or file_storage.filename == '':
        return 0, "未选择 ZIP 文件"

    imported = 0
    try:
        with ZipFile(file_storage) as zf:
            # 找 CSV / TXT
            csv_name = None
            for name in zf.namelist():
                lower = name.lower()
                if lower.endswith('.csv') or lower.endswith('.txt'):
                    csv_name = name
                    break
            if not csv_name:
                return 0, "ZIP 中未找到 CSV/TXT 文件"

            raw = zf.read(csv_name)
            # 尝试几种常见编码
            text = None
            for enc in ('utf-8-sig', 'utf-8', 'gbk'):
                try:
                    text = raw.decode(enc)
                    break
                except UnicodeDecodeError:
                    continue
            if text is None:
                return 0, "CSV 编码无法识别（尝试了 utf-8 / gbk）"

            reader = csv.reader(text.splitlines())
            for row in reader:
                if not row:
                    continue
                # 允许 "Tell Your World 海选赛 萌新组 tyw.png" 这种没有逗号的情况：
                # 或者 "Tell Your World,海选赛,萌新组,tyw.png"
                
                line_str = ','.join(row) # 重新拼回去处理，或者直接处理 list
                # 简单点，如果 row 长度 >= 3，就按列取
                
                # 1. 解析字段
                s_name = row[0].strip()
                s_phase_raw = row[1].strip() if len(row) > 1 else ''
                s_group_raw = row[2].strip() if len(row) > 2 else ''
                s_img = row[3].strip() if len(row) > 3 else ''
                
                # 如果没用逗号，而是空格分隔（比如 txt）
                if len(row) == 1 and (' ' in s_name):
                    parts = s_name.split()
                    s_name = parts[0]
                    if len(parts) > 1: s_phase_raw = parts[1]
                    if len(parts) > 2: s_group_raw = parts[2]
                    if len(parts) > 3: s_img = parts[3]

                # 2. 映射赛程/组别
                phase_map = {
                    '海选': 'qualifier', '海选赛': 'qualifier', 'qualifier': 'qualifier',
                    '复活': 'revival', '复活赛': 'revival', 'revival': 'revival',
                    '半决赛': 'semifinal', 'semifinal': 'semifinal',
                    '决赛': 'final', 'final': 'final'
                }
                group_map = {
                    '萌新': 'beginner', '萌新组': 'beginner', 'beginner': 'beginner',
                    '进阶': 'advanced', '进阶组': 'advanced', 'advanced': 'advanced',
                    '巅峰': 'peak', '巅峰组': 'peak', 'peak': 'peak'
                }
                
                db_phase = phase_map.get(s_phase_raw)
                db_group = group_map.get(s_group_raw)
                
                # 3. 如果 ZIP 里有对应图片，保存它
                final_img_name = None
                if s_img:
                    # 在 zip 里找这个文件 (忽略大小写)
                    found_img_key = None
                    for zname in zf.namelist():
                        if zname.lower().endswith(s_img.lower()):
                            found_img_key = zname
                            break
                    
                    if found_img_key:
                        # 提取并保存到 static/songs
                        ext = os.path.splitext(found_img_key)[1].lower()
                        # 生成一个安全的文件名 (uuid)
                        safe_filename = f"{uuid.uuid4().hex}{ext}"
                        target_path = os.path.join(app.config['SONG_FOLDER'], safe_filename)
                        
                        with open(target_path, 'wb') as f_out:
                            f_out.write(zf.read(found_img_key))
                        final_img_name = safe_filename

                if s_name and db_phase and db_group:
                    db.session.add(Song(
                        name=s_name,
                        phase=db_phase,
                        group=db_group,
                        image_filename=final_img_name
                    ))
                    imported += 1

        db.session.commit()
        return imported, None

    except Exception as e:
        print(f"[import_songs] Error: {e}")
        return 0, f"导入出错: {str(e)}"


    except BadZipFile:
        return 0, "文件不是有效的 ZIP 压缩包"
    except Exception as e:
        db.session.rollback()
        print("[import_songs_from_zip] ERROR:", repr(e))
        return imported, str(e)


@app.route('/api/auth/check_status', methods=['POST'])
def api_auth_check_status():
    """
    检查选手状态 (用于登录/注册流程)
    POST { "name": "..." }
    """
    data = request.get_json()
    name = (data.get('name') or '').strip()
    if not name:
        return api_response(False, message='请输入姓名')

    player = Player.query.filter_by(name=name).first()
    if not player:
        return api_response(True, data={'exists': False, 'registered': False, 'avatar_url': None})

    return api_response(True, data={
        'exists': True,
        'registered': bool(player.password_hash),
        'avatar_url': url_for('static', filename=f'avatars/{player.avatar_filename}') if player.avatar_filename else None
    })


@app.route('/api/auth/register', methods=['POST'])
def api_auth_register():
    """
    注册 (首次设置密码 + 头像)
    Form Data: name, password, avatar (file)
    """
    if not get_system_state().checkin_enabled:
        return api_response(False, message='签到尚未开放')

    try:
        name = request.form.get('name', '').strip()
        password = request.form.get('password', '').strip()
        
        if not name or not password:
            return api_response(False, message='姓名和密码不能为空')

        player = Player.query.filter_by(name=name).first()
        if not player:
            return api_response(False, message='未找到该选手信息，请联系管理员')

        if player.password_hash:
            return api_response(False, message='该账号已注册，请直接登录')

        # 设置密码
        player.set_password(password)

        # 处理头像
        file = request.files.get('avatar')
        if file and file.filename:
            filename = secure_filename(file.filename)
            # 使用 uuid 防止文件名冲突
            ext = os.path.splitext(filename)[1]
            new_filename = f"{uuid.uuid4().hex}{ext}"
            file.save(os.path.join(app.config['AVATAR_FOLDER'], new_filename))
            player.avatar_filename = new_filename

        # 自动签到 (如果尚未签到)
        if not player.checked_in:
             # 自动分配序号
            max_num = db.session.query(func.max(Player.match_number)).scalar() or 0
            player.match_number = max_num + 1
            player.checked_in = True
        
        db.session.commit()

        # 登录 (设置 Cookie)
        # 注意: api_response 返回的是 (json, code) 元组，我们需要构造 response 对象来设置 cookie
        resp_json, code = api_response(True, message='注册成功')
        resp = make_response(resp_json, code)
        resp.set_cookie('player_id', str(player.id), max_age=30 * 24 * 60 * 60)
        return resp

    except Exception as e:
        db.session.rollback()
        print(f"[Register Error] {e}")
        return api_response(False, message='注册失败，服务器内部错误', code=500)


@app.route('/api/auth/login', methods=['POST'])
def api_auth_login():
    """
    登录
    JSON: { "name": "...", "password": "..." }
    """
    if not get_system_state().checkin_enabled:
        return api_response(False, message='签到尚未开放')

    data = request.get_json()
    name = (data.get('name') or '').strip()
    password = (data.get('password') or '').strip()

    if not name or not password:
        return api_response(False, message='请输入姓名和密码')

    player = Player.query.filter_by(name=name).first()
    if not player:
        return api_response(False, message='用户不存在')

    if not player.password_hash:
        return api_response(False, message='该账号尚未激活，请先注册')

    if not player.check_password(password):
        return api_response(False, message='密码错误')

    # 登录成功
    # 检查签到状态，如果没有签到则签到
    if not player.checked_in:
        max_num = db.session.query(func.max(Player.match_number)).scalar() or 0
        player.match_number = max_num + 1
        player.checked_in = True
        db.session.commit()
    
    resp_json, code = api_response(True, message='登录成功')
    resp = make_response(resp_json, code)
    resp.set_cookie('player_id', str(player.id), max_age=30 * 24 * 60 * 60)
    return resp

# ================= 路由：选手端 =================

@app.route('/', methods=['GET', 'POST'])
def index():
    """
    选手签到 / 状态查询
    使用 cookie 中的 player_id 识别选手，不再写入中文姓名到 cookie。
    """
    try:
        player = None
        player_id_cookie = request.cookies.get('player_id')

        # 优先用 cookie 里的 player_id
        if player_id_cookie and player_id_cookie.isdigit():
            player = Player.query.get(int(player_id_cookie))
            if player:
                if not player.checked_in:
                    # 自动分配序号
                    max_num = db.session.query(func.max(Player.match_number)).scalar() or 0
                    player.match_number = max_num + 1

                    player.checked_in = True
                    db.session.commit()
                    flash(f"✅ 签到成功！您的比赛序号是：{player.match_number}", "success")
                return render_template('index.html', player=player)
            else:
                # cookie 失效，清理
                resp = make_response(redirect(url_for('index')))
                resp.delete_cookie('player_id')
                resp.delete_cookie('player_name')  # 兼容旧版本残留
                flash("登录信息失效，请重新输入姓名。", "danger")
                return resp

        # POST：输入姓名签到
        if request.method == 'POST':
            name = (request.form.get('name') or '').strip()
            if not name:
                flash("请输入报名姓名。", "warning")
                return render_template('index.html', player=None)

            player = Player.query.filter_by(name=name).first()
            if not player:
                flash("未找到该选手，请确认姓名是否正确（或联系管理员）。", "danger")
                return render_template('index.html', player=None)

            if not player.checked_in:
                # 自动分配序号：查出当前已有的最大序号
                max_num = db.session.query(func.max(Player.match_number)).scalar() or 0
                player.match_number = max_num + 1

                player.checked_in = True
                db.session.commit()
                flash(f"✅ 签到成功！您的比赛序号是：{player.match_number}", "success")

            resp = make_response(render_template('index.html', player=player))
            resp.set_cookie('player_id', str(player.id), max_age=30 * 24 * 60 * 60)
            # 清理旧的姓名 cookie
            resp.delete_cookie('player_name')
            return resp

        # GET 且没有 cookie：显示输入姓名页面
        return render_template('index.html', player=None)

    except Exception as e:
        print("[index] ERROR:", repr(e))
        flash("前台页面加载失败，请联系工作人员。", "danger")
        # 尽量仍然返回一个页面，而不是纯 500
        return render_template('index.html', player=None)


@app.route('/logout')
def logout():
    resp = make_response(redirect(url_for('index')))
    resp.delete_cookie('player_id')
    resp.delete_cookie('player_name')  # 兼容旧版本
    flash('您已成功退出登录。', 'info')
    return resp


@app.route('/toggle_machine', methods=['POST'])
def toggle_machine():
    try:
        player_id_cookie = request.cookies.get('player_id')
        if not player_id_cookie or not player_id_cookie.isdigit():
            flash('登录状态失效，请重新登录。', 'danger')
            return redirect(url_for('index'))

        player = Player.query.get(int(player_id_cookie))
        if not player or not player.checked_in:
            flash('未找到您的签到信息或您未签到。', 'danger')
            return redirect(url_for('index'))

        if player.promotion_status == 'eliminated':
            flash('当前为淘汰状态，无法进行上机/下机操作。', 'warning')
            return redirect(url_for('index'))

        player.on_machine = not player.on_machine
        db.session.commit()
        flash('上机状态已更新。', 'info')
    except SQLAlchemyError as e:
        db.session.rollback()
        print("[toggle_machine] DB ERROR:", e)
        flash('更新上机状态失败，请联系工作人员。', 'danger')
    except Exception as e:
        print("[toggle_machine] ERROR:", repr(e))
        flash('系统错误，请联系工作人员。', 'danger')

    return redirect(url_for('index'))


@app.route('/player_state_api')
def player_state_api():
    """
    选手端轮询自己的状态，用来决定是否自动刷新页面。
    只要返回的 JSON 内容有变化，前端就会 reload。
    """
    try:
        player_id_cookie = request.cookies.get('player_id')
        if not player_id_cookie or not player_id_cookie.isdigit():
            # 没有登录信息，就返回一个简单状态
            return jsonify({
                "ok": False,
                "reason": "no_player"
            })

        player = Player.query.get(int(player_id_cookie))
        if not player:
            return jsonify({
                "ok": False,
                "reason": "not_found"
            })

        # 把和页面展示相关的字段打包返回
        data = {
            "ok": True,
            "id": player.id,
            "name": player.name,
            "rating": player.rating,
            "group": player.group,                  # beginner / advanced
            "checked_in": player.checked_in,
            "on_machine": player.on_machine,
            "match_number": player.match_number,
            "score_round1": player.score_round1,
            "score_revival": player.score_revival,
            "promotion_status": player.promotion_status,
            "match_started": get_system_state().match_started # Inject match state
        }
        return jsonify(data)

    except Exception as e:
        print("[player_state_api] ERROR:", repr(e))
        return jsonify({"ok": False, "reason": "error"}), 500


@app.route('/submit_score', methods=['POST'])
def submit_score():
    try:
        player_id_cookie = request.cookies.get('player_id')
        score_str = (request.form.get('score') or '').strip()

        if not player_id_cookie or not player_id_cookie.isdigit():
            flash('登录状态失效，请重新登录。', 'danger')
            return redirect(url_for('index'))

        player = Player.query.get(int(player_id_cookie))
        if not player or not player.checked_in:
            flash('未找到您的签到信息或您未签到。', 'danger')
            return redirect(url_for('index'))

        try:
            score = float(score_str)
        except ValueError:
            flash('成绩输入格式不正确，请输入有效数字。', 'danger')
            return redirect(url_for('index'))

        # 提交成绩后自动下机
        player.on_machine = False

        # 海选
        if player.score_round1 is None:
            player.score_round1 = score
            db.session.commit()
            flash(f'成绩已提交！您的海选成绩为：{score}。请等待结果公布。', 'success')
            return redirect(url_for('index'))

        # 复活赛
        if player.promotion_status == 'revival' and player.score_revival is None:
            player.score_revival = score
            db.session.commit()
            flash(f'成绩已提交！您的复活赛成绩为：{score}。请等待结果公布。', 'success')
            return redirect(url_for('index'))

        flash('当前阶段无需提交成绩，请联系工作人员确认。', 'warning')
        db.session.commit()
    except SQLAlchemyError as e:
        db.session.rollback()
        print("[submit_score] DB ERROR:", e)
        flash('成绩提交失败，请联系工作人员。', 'danger')
    except Exception as e:
        print("[submit_score] ERROR:", repr(e))
        flash('系统错误，请联系工作人员。', 'danger')

    return redirect(url_for('index'))


# ================= 路由：后台登录 =================

@app.route('/admin_login', methods=['GET', 'POST'])
def admin_login():
    if request.method == 'POST':
        username = (request.form.get('username') or '').strip()
        password = (request.form.get('password') or '').strip()
        # 简单账号密码：可以按需改
        if username == 'admin' and password == 'admin888':
            session['admin_logged_in'] = True
            flash("后台登录成功。", "success")
            return redirect(url_for('admin'))
        flash("账号或密码错误。", "danger")
    return render_template('admin_login.html')


@app.route('/admin_logout')
def admin_logout():
    session.pop('admin_logged_in', None)
    flash("您已退出后台。", "info")
    return redirect(url_for('admin_login'))


@app.route('/admin/qrcode')
def admin_qrcode():
    if not require_admin():
        return redirect(url_for('admin_login'))
    return render_template('qrcode_gen.html')


@app.route('/api/admin/search_player', methods=['GET'])
def api_admin_search_player():
    if not require_admin():
        return api_response(False, message='Unauthorized', code=401)
         
    name = request.args.get('name', '').strip()
    if not name:
        return api_response(False, message='Empty name')
        
    players = Player.query.filter(Player.name.like(f'%{name}%')).all()
    if not players:
        return api_response(False, message='Player not found')
        
    data = [{
        'id': p.id,
        'name': p.name,
        'group': p.group
    } for p in players]
    
    return api_response(True, data=data)


def require_admin():
    if not session.get('admin_logged_in'):
        return False
    return True


# ================= 路由：管理员后台 =================

@app.route('/admin', methods=['GET', 'POST'])
def admin():
    if not require_admin():
        return redirect(url_for('admin_login'))

    state = get_system_state()

    if request.method == 'POST':
        action = request.form.get('action')

        # -------- 1. 导入选手名单 ----------
        if action == 'add':
            default_group = request.form.get('player_group', 'beginner')
            pending_entries = []
            added_count = 0

            # A. CSV 文件
            csv_file = request.files.get('csv_file')
            if csv_file and csv_file.filename.lower().endswith('.csv'):
                try:
                    try:
                        stream = TextIOWrapper(csv_file.stream, encoding='utf-8-sig', errors='strict')
                        csv_input = csv.reader(stream)
                        rows = list(csv_input)
                    except UnicodeDecodeError:
                        csv_file.stream.seek(0)
                        stream = TextIOWrapper(csv_file.stream, encoding='gbk', errors='ignore')
                        csv_input = csv.reader(stream)
                        rows = list(csv_input)

                    for row in rows:
                        if not row:
                            continue
                        p_name = row[0].strip()
                        p_rating = 0
                        if len(row) > 1 and str(row[1]).strip().isdigit():
                            p_rating = int(str(row[1]).strip())

                        p_group = None
                        if len(row) > 2:
                            raw_group = str(row[2]).strip().lower()
                            if raw_group in ['萌新组', '萌新', 'beginner']:
                                p_group = 'beginner'
                            elif raw_group in ['进阶组', '进阶', 'advanced']:
                                p_group = 'advanced'
                            elif raw_group in ['巅峰组', '巅峰', 'peak']:
                                p_group = 'peak'
                        if p_name:
                            pending_entries.append((p_name, p_rating, p_group))
                except Exception as e:
                    print("[admin-add-csv] ERROR:", repr(e))
                    flash(f'CSV 文件解析出错: {e}', 'danger')

            # B. 文本框
            lines = (request.form.get('names') or '').split('\n')
            for line in lines:
                line = line.strip()
                if not line:
                    continue
                parts = line.rsplit(maxsplit=1)
                t_rating = 0
                t_name = line
                if len(parts) == 2 and parts[1].isdigit():
                    t_name = parts[0]
                    t_rating = int(parts[1])
                if t_name:
                    pending_entries.append((t_name, t_rating, None))

            # C. 写入数据库
            for name_val, rating_val, group_val in pending_entries:
                if not Player.query.filter_by(name=name_val).first():
                    final_group = group_val if group_val else default_group
                    db.session.add(Player(name=name_val, group=final_group, rating=rating_val))
                    added_count += 1

            try:
                db.session.commit()
                flash(f'成功添加 {added_count} 名选手。', 'success')
            except Exception as e:
                db.session.rollback()
                flash(f'添加失败: {e}', 'danger')

            return redirect(url_for('admin'))

        # -------- 2. 结束签到并随机分组 ----------
        if action == 'generate':
            if state.match_generated:
                flash('当前“结束签到并生成分组随机序号”操作已锁定，请先输入密码解锁。', 'warning')
                return redirect(url_for('admin'))

            try:
                beginner_players = Player.query.filter_by(checked_in=True, group='beginner').all()
                advanced_players = Player.query.filter_by(checked_in=True, group='advanced').all()

                total_numbered = 0

                if not beginner_players and not advanced_players:
                    flash('当前无人签到，无法生成序号。', 'warning')
                else:
                    if beginner_players:
                        random.shuffle(beginner_players)
                        nums = list(range(1, len(beginner_players) + 1))
                        for p, num in zip(beginner_players, nums):
                            p.match_number = num
                        total_numbered += len(beginner_players)

                    if advanced_players:
                        random.shuffle(advanced_players)
                        nums = list(range(1, len(advanced_players) + 1))
                        for p, num in zip(advanced_players, nums):
                            p.match_number = num
                        total_numbered += len(advanced_players)

                    state.match_generated = True
                    db.session.commit()
                    flash(f'✅ 成功为 {total_numbered} 名已签到选手分配了随机序号！', 'success')
            except Exception as e:
                db.session.rollback()
                print("[admin-generate] ERROR:", repr(e))
                flash('生成随机序号失败，请检查日志。', 'danger')

            return redirect(url_for('admin'))

        # -------- 2.1 解锁 generate ----------
        if action == 'unlock_generate':
            pwd = (request.form.get('generate_password') or '').strip()
            if pwd != '1145141919810ax':
                flash('解锁密码错误。', 'danger')
                return redirect(url_for('admin'))
            state.match_generated = False
            db.session.commit()
            flash('已成功解锁“结束签到并生成分组随机序号”按钮。', 'success')
            return redirect(url_for('admin'))

        # -------- 2.2 测试用：一键开启签到和比赛（无倒计时） ----------
        if action == 'test_start_match':
            pwd = (request.form.get('test_start_password') or '').strip()
            if pwd != '1145141919810ax':
                flash('密码错误。', 'danger')
                return redirect(url_for('admin'))
            
            state.checkin_enabled = True
            state.match_started = True
            # 不设置 start_time，或者设置为 None，并在 api_system_state 中处理
            # 经分析，若 start_time 为 None，elapsed 计算会报错或 remaining=0 触发 timeout。
            # 为了“无倒计时且不超时”，我们将其设置为很久以后的时间，或者修改 api_system_state
            # 这里选择修改 api_system_state 来支持 "无倒计时模式" 比较稳妥
            # 但最简单的方法是设置 start_time 为 None，并在 api_system_state 中返回 remaining = -1
            state.start_time = None 
            state.checkin_timeout_processed = False # 重置超时状态
            
            db.session.commit()
            flash('测试模式已开启：签到已开放，比赛已开始（无倒计时）。', 'success')
            return redirect(url_for('admin'))

        # -------- 3. 生成 16 强 + 复活赛 ----------
        # -------- 3. 生成 16 强 + 复活赛 + 巅峰组晋级 ----------
        if action == 'promote_16':
            try:
                cnt = promote_qualifier_logic()
                flash(f'已根据海选成绩生成晋级名单（{cnt} 人状态已更新）。含萌新组、巅峰组新规则。', 'success')
            except Exception as e:
                db.session.rollback()
                print("[admin-promote_16] ERROR:", repr(e))
                flash(f'生成晋级名单失败: {e}', 'danger')
            return redirect(url_for('admin'))

        # -------- 3.1 自动生成对战 (Matchmaking) ----------
        if action == 'create_matches':
            target_phase = request.form.get('target_phase')
            target_group = request.form.get('target_group')
            
            if not target_phase or not target_group:
                flash('请指定赛程和组别。', 'warning')
            else:
                try:
                    count, msg = auto_create_matches(target_phase, target_group)
                    if count > 0:
                        flash(f'{msg} ({count} 场)。', 'success')
                    else:
                        flash(f'生成的对局数为 0，提示：{msg}', 'warning')
                except Exception as e:
                    print(f"[create_matches] Error: {e}")
                    flash(f'生成对战失败: {str(e)}', 'danger')
            
            return redirect(url_for('admin'))

        # -------- 4. 4 强说明 ----------
        if action == 'promote_4':
            flash('4 强阶段不再使用系统自动划分，请在下方表格的“状态”列中手动设置选手为季军、殿军、亚军或冠军。', 'info')
            return redirect(url_for('admin'))

        # -------- 5. 排序 ----------
        if action == 'sort_scores':
            flash('已按海选成绩排序显示。', 'info')
            return redirect(url_for('admin', sort='score', q=request.args.get('q', '')))
        if action == 'sort_rating':
            flash('已按组别 + Rating 排序显示。', 'info')
            return redirect(url_for('admin', sort='rating', q=request.args.get('q', '')))

        # -------- 6. 保存全部修改 ----------
        if action == 'save_all':
            try:
                players = Player.query.all()
                for p in players:
                    grp_val = request.form.get(f'group_{p.id}')
                    if grp_val in ['beginner', 'advanced', 'peak']:
                        p.group = grp_val

                    mn_str = (request.form.get(f'match_number_{p.id}') or '').strip()
                    p.match_number = int(mn_str) if mn_str else None

                    sr1 = (request.form.get(f'score_round1_{p.id}') or '').strip()
                    if sr1:
                        try:
                            p.score_round1 = float(sr1)
                        except ValueError:
                            pass

                    sr2 = (request.form.get(f'score_revival_{p.id}') or '').strip()
                    if sr2:
                        try:
                            p.score_revival = float(sr2)
                        except ValueError:
                            pass

                    st = (request.form.get(f'status_{p.id}') or 'none').strip()
                    p.promotion_status = st

                    ko16 = (request.form.get(f'ko16_8_result_{p.id}') or '').strip()
                    if ko16 == 'to8':
                        p.promotion_status = 'top8'
                    elif ko16 == 'out16':
                        p.promotion_status = 'top16_out'

                    ko8 = (request.form.get(f'ko8_4_result_{p.id}') or '').strip()
                    if ko8 == 'to4':
                        p.promotion_status = 'top4'
                    elif ko8 == 'out8':
                        p.promotion_status = 'top8_out'

                    # 4 -> 2 阶段（萌新组 / 巅峰组）
                    ko42 = (request.form.get(f'ko4_2_result_{p.id}') or '').strip()
                    if ko42 == 'to_final':
                        p.promotion_status = 'final'
                    elif ko42 == 'out4':
                        # 负者保留在 Top4（等待最终名次手动设置）
                        if p.group == 'peak':
                            p.promotion_status = 'top4_peak'
                        else:
                            p.promotion_status = 'top4'

                db.session.commit()
                flash('所有修改已保存。', 'success')
            except Exception as e:
                db.session.rollback()
                print("[admin-save_all] ERROR:", repr(e))
                flash('保存失败，请检查日志。', 'danger')
            return redirect(url_for('admin'))

        # -------- 7. 删除指定选手 ----------
        if action == 'delete_player':
            try:
                p_id = request.form.get('player_id')
                if p_id:
                    player_to_delete = Player.query.get(int(p_id))
                    if player_to_delete:
                        db_name = player_to_delete.name
                        db.session.delete(player_to_delete)
                        db.session.commit()
                        flash(f'已删除选手：{db_name}', 'success')
                    else:
                        flash('未找到该选手，删除失败。', 'warning')
            except Exception as e:
                db.session.rollback()
                print("[admin-delete_player] ERROR:", repr(e))
                flash(f'删除失败: {e}', 'danger')
            return redirect(url_for('admin'))

        # -------- 7. 批量复活 / 标记 ----------
        if action == 'revive_selected':
            ids = request.form.getlist('selected_players')
            if not ids:
                flash('请先勾选需要复活的选手。', 'warning')
                return redirect(url_for('admin'))
            try:
                players = Player.query.filter(Player.id.in_(ids)).all()
                cnt = 0
                for p in players:
                    p.promotion_status = 'top16'
                    cnt += 1
                db.session.commit()
                flash(f'已将 {cnt} 名选手标记为本组 16 强。', 'success')
            except Exception as e:
                db.session.rollback()
                print("[admin-revive_selected] ERROR:", repr(e))
                flash(f'操作失败: {e}', 'danger')
            return redirect(url_for('admin'))

        if action == 'mark_top8_selected':
            ids = request.form.getlist('selected_players')
            if not ids:
                flash('请先勾选需要标记的选手。', 'warning')
                return redirect(url_for('admin'))
            try:
                players = Player.query.filter(Player.id.in_(ids)).all()
                cnt = 0
                for p in players:
                    p.promotion_status = 'top8'
                    cnt += 1
                db.session.commit()
                flash(f'已将 {cnt} 名选手标记为本组 8 强。', 'success')
            except Exception as e:
                db.session.rollback()
                print("[admin-mark_top8_selected] ERROR:", repr(e))
                flash(f'操作失败: {e}', 'danger')
            return redirect(url_for('admin'))

        # -------- 8. 批量删除 ----------
        if action == 'delete_selected':
            ids = request.form.getlist('selected_players')
            if not ids:
                flash('请先勾选需要删除的选手。', 'warning')
                return redirect(url_for('admin'))
            try:
                # 批量删除
                del_cnt = Player.query.filter(Player.id.in_(ids)).delete(synchronize_session=False)
                db.session.commit()
                flash(f'已成功删除 {del_cnt} 名选手。', 'success')
            except Exception as e:
                db.session.rollback()
                print("[admin-delete_selected] ERROR:", repr(e))
                flash(f'批量删除失败: {e}', 'danger')
            return redirect(url_for('admin'))

        # -------- 8. 批量淘汰 ----------
        if action == 'eliminate_selected':
            ids = request.form.getlist('selected_players')
            if not ids:
                flash('请先勾选需要淘汰的选手。', 'warning')
                return redirect(url_for('admin'))
            try:
                Player.query.filter(Player.id.in_(ids)).update(
                    {Player.promotion_status: 'eliminated'},
                    synchronize_session=False
                )
                db.session.commit()
                flash(f'已将 {len(ids)} 名选手标记为淘汰。', 'success')
            except Exception as e:
                db.session.rollback()
                print("[admin-eliminate_selected] ERROR:", repr(e))
                flash('标记淘汰失败，请检查日志。', 'danger')
            return redirect(url_for('admin'))

        # -------- 9. 清空所有数据 ----------
        if action == 'clear_all_secure':
            password = (request.form.get('clear_password') or '').strip()
            if password != '1145141919810ax':
                flash("清除数据密码错误。", "danger")
                return redirect(url_for('admin'))
            try:
                deleted = Player.query.delete()
                state.match_generated = False
                db.session.commit()
                flash(f"已清除所有选手数据（共 {deleted} 条），并重置系统。", "warning")
            except Exception as e:
                db.session.rollback()
                print("[admin-clear_all_secure] ERROR:", repr(e))
                flash("清除数据失败，请检查日志。", "danger")
            return redirect(url_for('admin'))

        # -------- 10. 曲目添加 ----------
        if action == 'add_song':
            # 优先 ZIP
            song_package = request.files.get('song_package')
            if song_package and song_package.filename:
                count, err = import_songs_from_zip(song_package)
                if count > 0 and not err:
                     flash(f"已从压缩包导入 {count} 首曲目。", "success")
                elif err:
                    flash(f"ZIP 导入完成：成功 {count} 首，提示信息：{err}", "warning")
                else:
                    flash(f"未从压缩包中导入任何曲目。", "warning")
                return redirect(url_for('admin'))

            # 批量曲名
            song_phase = request.form.get('song_phase', 'qualifier')
            song_group = request.form.get('song_group', 'beginner')
            song_name = (request.form.get('song_name') or '').strip()
            song_image_fs = request.files.get('song_image')
            bulk_text = (request.form.get('song_names_bulk') or '').strip()

            added = 0

            # 批量
            if bulk_text:
                for line in bulk_text.splitlines():
                    line = line.strip()
                    if not line:
                        continue
                    db.session.add(Song(
                        name=line,
                        phase=song_phase,
                        group=song_group
                    ))
                    added += 1

            # 单曲
            if song_name:
                img_filename = save_song_image_from_file(song_image_fs)
                db.session.add(Song(
                    name=song_name,
                    phase=song_phase,
                    group=song_group,
                    image_filename=img_filename
                ))
                added += 1

            try:
                db.session.commit()
                flash(f"成功添加 {added} 首曲目。", "success")
            except Exception as e:
                db.session.rollback()
                print("[admin-add_song] ERROR:", repr(e))
                flash("添加曲目失败，请检查日志。", "danger")

            return redirect(url_for('admin'))

        # -------- 11. 曲目删除 ----------
        if action == 'delete_song':
            song_id = request.form.get('song_id')
            try:
                if song_id and song_id.isdigit():
                    s = Song.query.get(int(song_id))
                    if s:
                        db.session.delete(s)
                        db.session.commit()
                        flash("曲目已删除。", "success")
            except Exception as e:
                db.session.rollback()
                print("[admin-delete_song] ERROR:", repr(e))
                flash("删除曲目失败，请检查日志。", "danger")
            return redirect(url_for('admin'))

        flash('未识别的操作。', 'warning')
        return redirect(url_for('admin'))

    # ============ GET 渲染后台页面 ============

    sort_by = request.args.get('sort')
    name_query = (request.args.get('q') or '').strip()

    players = get_players_data(sort_by=sort_by, name_query=name_query)
    stats = get_dashboard_stats()
    state = get_system_state()

    # 曲目列表按赛程 + 组别
    songs_q_beg = Song.query.filter_by(phase='qualifier', group='beginner', active=True).all()
    songs_q_adv = Song.query.filter_by(phase='qualifier', group='advanced', active=True).all()
    songs_r_beg = Song.query.filter_by(phase='revival', group='beginner', active=True).all()
    songs_r_adv = Song.query.filter_by(phase='revival', group='advanced', active=True).all()
    songs_s_beg = Song.query.filter_by(phase='semifinal', group='beginner', active=True).all()
    songs_s_adv = Song.query.filter_by(phase='semifinal', group='advanced', active=True).all()
    songs_f_beg = Song.query.filter_by(phase='final', group='beginner', active=True).all()
    songs_f_adv = Song.query.filter_by(phase='final', group='advanced', active=True).all()
    # 巅峰组只有海选需要曲库（4强/决赛自选）
    songs_q_peak = Song.query.filter_by(phase='qualifier', group='peak', active=True).all()

    return render_template(
        'admin.html',
        players=players,
        sort_message='' if not sort_by else (
            '（按海选成绩排序）' if sort_by == 'score' else '（按组别 Rating 排序）'
        ),
        total=stats['total'],
        checked=stats['checked'],
        numbered=stats['numbered'],
        promoted_16=stats['promoted_16'],
        promoted_4=stats['promoted_4'],
        revival_count=stats['revival_count'],
        max_beg=stats['max_beg'],
        max_adv=stats['max_adv'],
        name_query=name_query,
        match_generated=state.match_generated,
        songs_q_beg=songs_q_beg,
        songs_q_adv=songs_q_adv,
        songs_r_beg=songs_r_beg,
        songs_r_adv=songs_r_adv,
        songs_s_beg=songs_s_beg,
        songs_s_adv=songs_s_adv,
        songs_f_beg=songs_f_beg,
        songs_f_adv=songs_f_adv,
        songs_q_peak=songs_q_peak,
    )


# ============ 新增：后台轮询状态 API（配合 admin.html 的 JS） ============

@app.route('/admin_state_api')
def admin_state_api():
    if not require_admin():
        return jsonify({"ok": False, "reason": "not_admin"}), 403

    stats = get_dashboard_stats()
    state = get_system_state()
    return jsonify({
        "ok": True,
        "total": stats['total'],
        "checked": stats['checked'],
        "numbered": stats['numbered'],
        "promoted_16": stats['promoted_16'],
        "promoted_4": stats['promoted_4'],
        "revival_count": stats['revival_count'],
        "match_started": state.match_started,
        "checkin_enabled": state.checkin_enabled
    })


# ================= 曲目抽选相关接口 =================

@app.route('/draw_screen')
def draw_screen():
    """
    抽选大屏界面：
    /draw_screen?phase=qualifier&group=beginner
    phase: qualifier / revival / semifinal / final
    """
    phase = request.args.get('phase', 'qualifier')
    group = request.args.get('group', 'beginner')
    if phase not in ['qualifier', 'revival', 'semifinal', 'final']:
        phase = 'qualifier'
    if group not in ['beginner', 'advanced']:
        group = 'beginner'
    return render_template('draw_screen.html', phase=phase, group=group)


@app.route('/song_draw_state_api')
def song_draw_state_api():
    """
    被大屏和选手端轮询，用来获取当前抽选状态。
    返回：
    - status: idle / rolling / finished
    - phase, group
    - songs: 当前赛程+组别的所有曲目（给大屏滚动用）
    - selected_song: 为兼容旧代码，返回第一首选中曲目或 None
    - selected_songs: 数组，最多 2 首
    """
    try:
        state = get_song_draw_state()
        if state.status == 'idle' or not state.phase or not state.group:
            return jsonify({
                "status": "idle",
                "phase": None,
                "group": None,
                "songs": [],
                "selected_song": None,
                "selected_songs": [],
                "updated_at": None,
            })

        songs = Song.query.filter_by(
            phase=state.phase,
            group=state.group,
            active=True
        ).all()

        songs_payload = []
        for s in songs:
            img_url = None
            if s.image_filename:
                img_url = url_for('static', filename=f'songs/{s.image_filename}', _external=False)
            songs_payload.append({
                "id": s.id,
                "name": s.name,
                "image_url": img_url,
            })

        selected_payload_list = []
        selected_songs = state.get_selected_songs()
        for s in selected_songs:
            img_url = None
            if s.image_filename:
                img_url = url_for('static', filename=f'songs/{s.image_filename}', _external=False)
            selected_payload_list.append({
                "id": s.id,
                "name": s.name,
                "image_url": img_url,
            })

        # 为兼容旧前端：selected_song 仍然保留第一首
        selected_first = selected_payload_list[0] if selected_payload_list else None

        return jsonify({
            "status": state.status,
            "phase": state.phase,
            "group": state.group,
            "songs": songs_payload,
            "selected_song": selected_first,
            "selected_songs": selected_payload_list,
            "updated_at": state.updated_at.isoformat() if state.updated_at else None,
        })
    except Exception as e:
        print("[song_draw_state_api] ERROR:", repr(e))
        return jsonify({
            "status": "idle",
            "phase": None,
            "group": None,
            "songs": [],
            "selected_song": None,
            "selected_songs": [],
        })


@app.route('/song_draw_control_api', methods=['POST'])
def song_draw_control_api():
    """
    被 draw_screen.html 内的 JS 调用：
    - action: 'start' or 'stop'
    - target: 'qualifier_beginner' 这样的字符串
    一次性随机 1~2 首曲目，并固化到 SongDrawState 中，前端只读不改。
    """
    try:
        data = request.get_json(silent=True) or {}
        action = (data.get('action') or '').strip()
        target = (data.get('target') or '').strip()

        if '_' not in target:
            return jsonify({"ok": False, "message": "目标参数不正确"}), 400

        phase, group = target.split('_', 1)
        if phase not in ['qualifier', 'revival', 'semifinal', 'final']:
            return jsonify({"ok": False, "message": "赛程参数不正确"}), 400
        if group not in ['beginner', 'advanced']:
            return jsonify({"ok": False, "message": "组别参数不正确"}), 400

        state = get_song_draw_state()

        if action == 'start':
            songs = Song.query.filter_by(
                phase=phase,
                group=group,
                active=True
            ).all()
            if not songs:
                return jsonify({"ok": False, "message": "当前赛程 / 组别下没有可用曲目"}), 400

            # 开始滚动：只设置状态，不选歌
            state.status = 'rolling'
            state.phase = phase
            state.group = group
            state.selected_song_ids = None
            db.session.commit()
            return jsonify({"ok": True, "message": "开始抽选"})

        if action == 'stop':
            songs = Song.query.filter_by(
                phase=phase,
                group=group,
                active=True
            ).all()
            if not songs:
                return jsonify({"ok": False, "message": "当前赛程 / 组别下没有可用曲目"}), 400

            # 一次性随机出 1~2 首曲目，顺序固定
            if len(songs) == 1:
                chosen_songs = [songs[0]]
            else:
                chosen_songs = random.sample(songs, k=min(2, len(songs)))

            state.status = 'finished'
            state.phase = phase
            state.group = group
            state.set_selected_songs(chosen_songs)
            db.session.commit()

            payload = [{
                "id": s.id,
                "name": s.name
            } for s in chosen_songs]

            return jsonify({
                "ok": True,
                "message": "抽选结束",
                "selected_songs": payload
            })

        return jsonify({"ok": False, "message": "未知操作"}), 400

    except Exception as e:
        db.session.rollback()
        print("[song_draw_control_api] ERROR:", repr(e))
        return jsonify({"ok": False, "message": "服务器错误"}), 500


# ============ 健康检查 + 全局错误处理 ============

@app.route('/ping')
def ping():
    return "pong"


#@app.errorhandler(Exception)
#def handle_exception(e):
#    """
#    全局兜底错误处理：
#    - HTTPException 直接返回原始响应（比如 404）
#    - 其它异常写入 flask_error.log，返回 500
#    """
#    if isinstance(e, HTTPException):
#        return e
#
#    try:
#        with open(ERROR_LOG_PATH, "a", encoding="utf-8") as f:
#            f.write("\n==== ERROR AT {} ====\n".format(datetime.utcnow().isoformat()))
#            f.write(repr(e) + "\n")
#    except Exception:
#        pass

#   return "Internal Server Error (logged).", 500


# ================= REST API 接口（供 Android App 调用） =================

def api_response(success=True, data=None, message=None, code=200):
    """统一的 API 响应格式"""
    resp = {'success': success, 'code': code}
    if data is not None:
        resp['data'] = data
    if message is not None:
        resp['message'] = message
    return jsonify(resp), code


def require_api_admin(f):
    """API 管理员认证装饰器"""
    from functools import wraps
    @wraps(f)
    def decorated_function(*args, **kwargs):
        # 1. 检查 Session (Web 后台)
        if session.get('is_admin') or session.get('admin_logged_in'):
            return f(*args, **kwargs)
            
        # 2. 检查 Header Token (App 或外部调用)
        token = request.headers.get('X-Admin-Token')
        if token == 'harbin_red_chart_2024':
            return f(*args, **kwargs)
            
        return api_response(False, message='需要管理员权限', code=401)
    return decorated_function


@app.route('/api/v1/admin/login', methods=['POST'])
def api_admin_login():
    """管理员登录 (API)"""
    data = request.get_json()
    password = data.get('password', '')
    
    # 支持网页后台密码 admin888 或 API 专用密码 api_password_2024
    if password in ['admin888', 'api_password_2024']:
        return api_response(True, data={'token': 'admin_token_2024'}, message='登录成功')
    
    return api_response(False, message='密码错误', code=401)


@app.route('/api/v1/admin/players', methods=['GET'])
@require_api_admin
def api_admin_get_players():
    """获取所有选手列表 (管理端)"""
    players = Player.query.all()
    data = []
    for p in players:
        data.append({
            'id': p.id,
            'name': p.name,
            'group': p.group,
            'match_number': p.match_number,
            'checked_in': p.checked_in,
            'on_machine': p.on_machine,
            'rating': p.rating,
            'score_round1': p.score_round1,
            'promotion_status': p.promotion_status,
            'forfeited': p.forfeited,
            'ban_used': p.ban_used
        })
    return api_response(True, data=data)


@app.route('/api/v1/dashboard', methods=['GET'])
def api_dashboard():
    """获取仪表盘统计信息"""
    stats = get_dashboard_stats()
    state = get_system_state()
    return api_response(True, data={
        'total_players': stats['total'],
        'checked_in': stats['checked'],
        'numbered': stats['numbered'],
        'promoted_16': stats['promoted_16'],
        'promoted_4': stats['promoted_4'],
        'revival_count': stats['revival_count'],
        'max_beginner_number': stats['max_beg'],
        'max_advanced_number': stats['max_adv'],
        'match_generated': state.match_generated
    })

# ================= 业务逻辑函数 =================

def get_active_match(player_id):
    """
    获取选手当前进行中的 1v1 对局
    状态为 pending 或 ongoing
    """
    return Match.query.filter(
        (Match.player1_id == player_id) | (Match.player2_id == player_id),
        Match.status.in_(['pending', 'ongoing'])
    ).first()

def handle_player_forfeit(player):
    """
    处理选手弃权逻辑
    """
    if player.forfeited:
        return False, "选手已弃权"
    
    player.forfeited = True
    
    # 1. 如果还在海选/复活赛阶段（非 1v1 对战状态）
    if player.promotion_status in ['none', 'revival']:
        player.promotion_status = 'eliminated'
        db.session.commit()
        return True, "弃权成功，已标记淘汰"
        
    # 2. 如果处于 1v1 赛程中
    match = get_active_match(player.id)
    if match:
        # 判定对手获胜
        opponent_id = match.player2_id if match.player1_id == player.id else match.player1_id
        winner = Player.query.get(opponent_id)
        
        match.winner_id = opponent_id
        match.status = 'finished'
        
        # 自动晋级对手
        # 根据当前阶段决定下一阶段状态
        next_status_map = {
            'top16': 'top8',
            'top8': 'top4',
            'top4': 'final', # 这里的 final 意味着进入冠亚军争夺，或者直接由 final 阶段产生冠军？
                             # 实际上 top4 胜者是进入 final (champion/runner_up争夺)
                             # 暂定 logic: top4 胜者 -> 'final_qualified' 或者保持 'top4' 但标记赢了?
                             # 简单起见，这里只处理胜负关系，具体 status 更新可能需要更复杂的逻辑
                             # 先调用通用晋级函数（如果实现的话），或者简单映射
        }
        
        # 弃权者淘汰
        if match.phase == 'top16':
            player.promotion_status = 'top16_out'
            if winner: winner.promotion_status = 'top8'
        elif match.phase == 'top8':
            player.promotion_status = 'top8_out'
            if winner: winner.promotion_status = 'top4'
        elif match.phase == 'top4':
             # 4进2，输者争季军，胜者争冠军
             # 此时无法直接定名次，只能标记赢家进入 final
             # 暂时标记为 winner.promotion_status = 'final_qualified' ? 
             # 或者现有系统中 top4 之后直接是 champion/runner_up
             # 让我们沿用：胜者保持 top4 并等待 status 更新，或者引入新状态
             # 简单起见：
             player.promotion_status = 'fourth' # 弃权者默认为殿军（如果是半决赛弃权）? 
                                                # 或者需要进一步区分。
             pass 
        elif match.phase == 'final':
             player.promotion_status = 'runner_up'
             if winner: winner.promotion_status = 'champion'
        
        db.session.commit()
        return True, f"弃权成功，对手 {winner.name if winner else ''} 自动晋级"
    
    db.session.commit()
    return True, "弃权成功"

def generate_pairings(phase, group):
    """
    为指定阶段和组别生成 1v1 配对
    规则：首尾匹配 (1 vs N, 2 vs N-1)
    """
    # 1. 找到该组别、该阶段的所有选手
    # 比如 phase='top16'，则找 status='top16' 的选手
    
    # 映射 phase 到需要的 promotion_status
    # 生成 16强对阵 -> 找 top16
    # 生成 8强对阵 -> 找 top8
    target_status = phase
    
    players = Player.query.filter_by(
        group=group, 
        promotion_status=target_status,
        forfeited=False # 已弃权的排除？或者包含但在生成时处理？
                        # 通常应在生成前处理，或者生成后直接判负
    ).all()
    
    if not players:
        return False, "未找到符合条件的选手"
        
    # 2. 排序
    # 排序规则：按上一轮成绩？还是按 Seed？
    # 现有系统 logic:
    # Top 16 是由 promote_16 action 生成的，当时应该已经有了成绩或种子序
    # 简单起见：按 score_round1 降序，如果 score_round1 相同或为空（如巅峰组可能无score_round1），按 rating desc
    # 巅峰组: score_round1 (海选成绩)
    
    # 定义排序 key
    def sort_key(p):
        s = p.score_round1 if p.score_round1 is not None else -1.0
        r = p.rating if p.rating is not None else 0
        return (s, r)
        
    players.sort(key=sort_key, reverse=True)
    
    count = len(players)
    if count < 2:
        return False, "选手人数不足以配对"
        
    matches = []
    # 首尾匹配
    mid = count // 2
    for i in range(mid):
        p1 = players[i]
        p2 = players[count - 1 - i]
        
        # 创建 Match
        m = Match(
            phase=phase,
            group=group,
            player1_id=p1.id,
            player2_id=p2.id,
            status='pending'
        )
        matches.append(m)
        
    if matches:
        db.session.add_all(matches)
        db.session.commit()
        return True, f"成功生成 {len(matches)} 组对阵"
    
    return False, "生成失败"




@app.route('/api/v1/player/checkin', methods=['POST'])
def api_player_checkin():
    """选手签到"""
    if not get_system_state().checkin_enabled:
        return api_response(False, message='签到尚未开放', code=400)

    data = request.get_json()
    if not data or not data.get('name'):
        return api_response(False, message='请提供选手姓名', code=400)
    
    name = data['name'].strip()
    player = Player.query.filter_by(name=name).first()
    
    if not player:
        return api_response(False, message=f'未找到选手 "{name}"', code=404)
    
    if player.promotion_status == 'timeout_eliminated':
        return api_response(False, message='您未能在签到截止前到达比赛现场，已取消您的参赛资格', code=400)

    if not player.checked_in:
        player.checked_in = True
        max_num = db.session.query(func.max(Player.match_number)).filter(
            Player.group == player.group, Player.checked_in == True
        ).scalar() or 0
        player.match_number = max_num + 1
        db.session.commit()
    
    return api_response(True, data={
        'id': player.id, 'name': player.name, 'group': player.group,
        'match_number': player.match_number, 'checked_in': player.checked_in,
        'avatar_url': url_for('static', filename=f'avatars/{player.avatar_filename}') if player.avatar_filename else None
    }, message='签到成功')


@app.route('/api/v1/player/<int:player_id>', methods=['GET'])
def api_get_player(player_id):
    """获取选手信息"""
    player = Player.query.get(player_id)
    if not player:
        return api_response(False, message='选手不存在', code=404)
    return api_response(True, data={
        'id': player.id, 'name': player.name, 'group': player.group,
        'match_number': player.match_number, 'checked_in': player.checked_in,
        'on_machine': player.on_machine, 'promotion_status': player.promotion_status,
        'rating': player.rating, 'score_round1': player.score_round1,
        'forfeited': player.forfeited, 'ban_used': player.ban_used,
        'match_started': get_system_state().match_started,
        'avatar_url': url_for('static', filename=f'avatars/{player.avatar_filename}') if player.avatar_filename else None
    })


@app.route('/api/v1/players', methods=['GET'])
def api_list_players():
    """获取选手列表"""
    query = Player.query
    group = request.args.get('group')
    if group:
        query = query.filter(Player.group == group)
    if request.args.get('checked_in') == 'true':
        query = query.filter(Player.checked_in == True)
    players = query.order_by(Player.name.asc()).all()
    return api_response(True, data=[{
        'id': p.id, 'name': p.name, 'group': p.group,
        'match_number': p.match_number, 'checked_in': p.checked_in,
        'rating': p.rating, 'score_round1': p.score_round1,
        'promotion_status': p.promotion_status,
        'forfeited': p.forfeited
    } for p in players])


@app.route('/api/v1/system/info', methods=['GET'])
def api_system_info():
    """获取系统信息"""
    state = get_system_state()
    return api_response(True, data={
        'app_name': '术力口大赛签到系统',
        'version': '1.0.0',
        'match_generated': state.match_generated,
        'match_started': state.match_started,
        'start_time': state.start_time.isoformat() if state.start_time else None
    })


@app.route('/api/v1/player/<int:player_id>/toggle_machine', methods=['POST'])
def api_toggle_machine(player_id):
    """切换选手上机状态"""
    player = Player.query.get(player_id)
    if not player:
        return api_response(False, message='选手不存在', code=404)
    if not player.checked_in:
        return api_response(False, message='请先签到', code=400)
    if player.promotion_status in ['eliminated', 'timeout_eliminated']:
        return api_response(False, message='淘汰状态下无法上机/下机', code=400)
    
    player.on_machine = not player.on_machine
    db.session.commit()
    
    status = '已标记上机' if player.on_machine else '已标记下机'
    return api_response(True, data={'on_machine': player.on_machine}, message=status)


@app.route('/api/v1/player/<int:player_id>/submit_score', methods=['POST'])
def api_submit_score(player_id):
    """选手提交成绩"""
    player = Player.query.get(player_id)
    if not player:
        return api_response(False, message='选手不存在', code=404)
    if not player.checked_in:
        return api_response(False, message='请先签到', code=400)
    
    data = request.get_json()
    score_str = data.get('score', '')
    
    try:
        score = float(score_str)
    except (ValueError, TypeError):
        return api_response(False, message='成绩格式错误', code=400)
    
    # 提交成绩后自动下机
    player.on_machine = False
    
    # 海选成绩
    if player.score_round1 is None:
        player.score_round1 = score
        db.session.commit()
        return api_response(True, data={
            'round': 'round1',
            'score': score
        }, message=f'海选成绩已提交：{score}')
    
    # 复活赛成绩
    if player.promotion_status == 'revival' and player.score_revival is None:
        player.score_revival = score
        db.session.commit()
        return api_response(True, data={
            'round': 'revival',
            'score': score
        }, message=f'复活赛成绩已提交：{score}')
    
    db.session.commit()
    return api_response(False, message='当前阶段无需提交成绩', code=400)


@app.route('/api/v1/player/search', methods=['GET'])
def api_search_player():
    """根据姓名搜索选手"""
    name = request.args.get('name', '').strip()
    if not name:
        return api_response(False, message='请提供选手姓名', code=400)
    
    player = Player.query.filter_by(name=name).first()
    if not player:
        return api_response(False, message=f'未找到选手 "{name}"', code=404)
    
    return api_response(True, data={
        'id': player.id,
        'name': player.name,
        'group': player.group,
        'group_label': '萌新组' if player.group == 'beginner' else ('进阶组' if player.group == 'advanced' else '巅峰组'),
        'match_number': player.match_number,
        'checked_in': player.checked_in,
        'on_machine': player.on_machine,
        'promotion_status': player.promotion_status,
        'rating': player.rating,
        'score_round1': player.score_round1,
        'score_revival': player.score_revival
    })


@app.route('/api/v1/song_draw/state', methods=['GET'])
def api_song_draw_state():
    try:
        state = get_song_draw_state()
        if state.status == 'idle' or not state.phase or not state.group:
            return api_response(True, data={
                'status': 'idle',
                'phase': None,
                'group': None,
                'songs': [],
                'selected_song': None,
                'selected_songs': [],
                'updated_at': None
            })

        songs = Song.query.filter(
            Song.phase == state.phase,
            Song.group == state.group,
            Song.active == True
        ).all()
        songs_payload = [{
            'id': s.id,
            'name': s.name,
            'image_url': f'/static/songs/{s.image_filename}' if s.image_filename else None
        } for s in songs]

        selected_songs = state.get_selected_songs()
        selected_list = [{
            'id': s.id,
            'name': s.name,
            'image_url': f'/static/songs/{s.image_filename}' if s.image_filename else None
        } for s in selected_songs]

        first_selected = (selected_list[0] if selected_list else None)

        return api_response(True, data={
            'status': state.status,
            'phase': state.phase,
            'group': state.group,
            'phase_label': {'qualifier': '海选赛', 'revival': '复活赛', 'semifinal': '半决赛', 'final': '决赛'}.get(state.phase, state.phase),
            'group_label': '萌新组' if state.group == 'beginner' else '进阶组',
            'songs': songs_payload,
            'selected_song': first_selected,
            'selected_songs': selected_list,
            'updated_at': state.updated_at.isoformat() if state.updated_at else None
        })
    except Exception:
        return api_response(False, message='获取抽选状态失败', code=500)


@app.route('/api/v1/songs', methods=['GET'])
def api_list_songs():
    """获取曲目列表"""
    query = Song.query.filter(Song.active == True)
    
    phase = request.args.get('phase')
    if phase:
        query = query.filter(Song.phase == phase)
    
    group = request.args.get('group')
    if group:
        query = query.filter(Song.group == group)
    
    songs = query.all()
    return api_response(True, data=[{
        'id': s.id,
        'name': s.name,
        'phase': s.phase,
        'group': s.group,
        'image_url': f'/static/songs/{s.image_filename}' if s.image_filename else None
    } for s in songs])


@app.route('/api/v1/rankings', methods=['GET'])
def api_rankings():
    """获取排行榜（按海选成绩排名）"""
    group = request.args.get('group')
    
    query = Player.query.filter(
        Player.checked_in == True,
        Player.score_round1 != None
    )
    
    if group:
        query = query.filter(Player.group == group)
    
    players = query.order_by(Player.score_round1.desc()).all()
    
    return api_response(True, data=[{
        'rank': idx + 1,
        'id': p.id,
        'name': p.name,
        'group': p.group,
        'group_label': '萌新组' if p.group == 'beginner' else ('进阶组' if p.group == 'advanced' else '巅峰组'),
        'score': p.score_round1,
        'promotion_status': p.promotion_status
    } for idx, p in enumerate(players)])


@app.route('/api/v1/on_machine', methods=['GET'])
def api_on_machine():
    """获取当前在机选手"""
    players = Player.query.filter(Player.on_machine == True).all()
    return api_response(True, data=[{
        'id': p.id,
        'name': p.name,
        'group': p.group,
        'group_label': '萌新组' if p.group == 'beginner' else ('进阶组' if p.group == 'advanced' else '巅峰组'),
        'match_number': p.match_number
    } for p in players])


# ================= 新增 API (Phase 2) =================




@app.route('/api/v1/song_draw/control', methods=['POST'])
def api_song_draw_control():
    data = request.get_json(silent=True) or {}
    action = (data.get('action') or '').strip()
    target = (data.get('target') or '').strip()
    phase = data.get('phase')
    group = data.get('group')

    if (not phase or not group) and '_' in target:
        parts = target.split('_', 1)
        phase = parts[0]
        group = parts[1]

    if phase not in ['qualifier', 'revival', 'semifinal', 'final']:
        return api_response(False, message='赛程参数不正确', code=400)
    if group not in ['beginner', 'advanced']:
        return api_response(False, message='组别参数不正确', code=400)

    state = get_song_draw_state()

    if action == 'start':
        songs = Song.query.filter_by(phase=phase, group=group, active=True).all()
        if not songs:
            return api_response(False, message='当前赛程/组别下没有可用曲目', code=400)
        state.status = 'rolling'
        state.phase = phase
        state.group = group
        state.selected_song_ids = None
        db.session.commit()
        return api_response(True, message='抽选已开始')
    elif action == 'stop':
        songs = Song.query.filter_by(phase=state.phase, group=state.group, active=True).all()
        if not songs:
            return api_response(False, message='没有可用曲目', code=400)
        count = 2 if len(songs) >= 2 else 1
        selected = random.sample(songs, count)
        state.status = 'finished'
        state.set_selected_songs(selected)
        db.session.commit()
        return api_response(True, data={
            'selected_songs': [{'id': s.id, 'name': s.name} for s in selected]
        })
    else:
        return api_response(False, message='非法操作', code=400)


# ================= 启动 =================

# ================= 业务逻辑函数 (Helpers) =================

def get_active_match(player_id):
    """
    获取选手当前进行中的 1v1 对局
    状态为 pending 或 ongoing
    """
    return Match.query.filter(
        (Match.player1_id == player_id) | (Match.player2_id == player_id),
        Match.status.in_(['pending', 'ongoing'])
    ).first()

def handle_player_forfeit(player):
    """
    处理选手弃权逻辑
    """
    if player.forfeited:
        return False, "选手已弃权"
    
    player.forfeited = True
    
    # 1. 如果还在海选/复活赛阶段
    if player.promotion_status in ['none', 'revival', 'eliminated']:
        player.promotion_status = 'eliminated'
        db.session.commit()
        return True, "弃权成功，已标记淘汰"
        
    # 2. 如果处于对战阶段 (含 1v1)
    match = get_active_match(player.id)
    if match:
        opponent_id = match.player2_id if match.player1_id == player.id else match.player1_id
        winner = Player.query.get(opponent_id)
        
        match.winner_id = opponent_id
        match.status = 'finished'
        
        # 简单状态流转 (Logic Simplified for MVP)
        if match.phase == 'top16':
            player.promotion_status = 'top16_out'
            if winner: winner.promotion_status = 'top8'
        elif match.phase == 'top8':
            player.promotion_status = 'top8_out'
            if winner: winner.promotion_status = 'top4'
        elif match.phase == 'top4' or match.phase == 'top4_peak':
            player.promotion_status = 'fourth' # 默认输半决赛为殿军(或进三四名赛)
            if winner: winner.promotion_status = 'final_qualified' 
        elif match.phase == 'final' or match.phase == 'final_peak':
            player.promotion_status = 'runner_up'
            if winner: winner.promotion_status = 'champion'
            
        db.session.commit()
        return True, f"弃权成功，对手 {winner.name if winner else ''} 自动晋级"
    
    # 其他情况 (如 top16 但还没开始 match) -> 直接淘汰
    if player.promotion_status not in ['eliminated']:
        player.promotion_status = 'eliminated'
        
    db.session.commit()
    return True, "弃权成功"


def promote_qualifier_logic():
    """
    海选晋级逻辑 (Configurable)
    """
    total_count = 0 
    
    for group_key, config in TOURNAMENT_CONFIG['groups'].items():
        if 'qualifier_promotion' not in config:
            continue
            
        # 获取该组别海选有成绩的选手
        players = Player.query.filter(
            Player.group == group_key, 
            Player.score_round1 != None, 
            Player.forfeited == False
        ).order_by(Player.score_round1.desc().nullslast(), Player.id.asc()).all()
        
        current_idx = 0
        for rule in config['qualifier_promotion']:
            target_status = rule['status']
            count = rule['count']
            
            # 晋级一批
            for _ in range(count):
                if current_idx < len(players):
                    players[current_idx].promotion_status = target_status
                    current_idx += 1
                    total_count += 1
        
        # 剩下的淘汰
        while current_idx < len(players):
            players[current_idx].promotion_status = 'eliminated'
            current_idx += 1
            total_count += 1
        
    db.session.commit()
    return total_count

def auto_create_matches(phase, group):
    """
    Min-Max Matching
    """
    # 针对巅峰组的特殊状态映射
    target_status = phase
    if group == 'peak' and phase == 'top4':
        target_status = 'top4_peak'

    cands = Player.query.filter(
        Player.group == group,
        Player.promotion_status == target_status,
        Player.forfeited == False
    ).all()
    
    if len(cands) < 2: return 0, "人数不足"
    
    # Sort: Score desc, then Rating desc
    cands.sort(key=lambda x: (x.score_round1 or -1, x.rating or 0), reverse=True)
    
    matches_new = 0
    mid = len(cands) // 2
    for i in range(mid):
        p1 = cands[i]
        p2 = cands[len(cands) - 1 - i]
        
        # Check exist
        exist = Match.query.filter(Match.phase==phase, Match.group==group, 
            Match.status.in_(['pending', 'ongoing']),
            ((Match.player1_id == p1.id) | (Match.player2_id == p1.id))
        ).first()
        if exist: continue
        
        m = Match(phase=phase, group=group, player1_id=p1.id, player2_id=p2.id, status='pending')
        db.session.add(m)
        matches_new += 1
        
    db.session.commit()
    return matches_new, "OK"


# ================= Phase 2 APIs =================

@app.route('/api/v1/player/<int:player_id>/forfeit', methods=['POST'])
def api_player_forfeit_endpoint(player_id):
    p = Player.query.get(player_id)
    if not p: return api_response(False, message="not found", code=404)
    res, msg = handle_player_forfeit(p)
    return api_response(res, message=msg)

@app.route('/api/v1/admin/promote_qualifier', methods=['POST'])
@require_api_admin
def api_admin_promote_qualifier():
    try:
        cnt = promote_qualifier_logic()
        return api_response(True, message=f"已更新 {cnt} 名选手的晋级状态")
    except Exception as e:
        return api_response(False, message=str(e), code=500)

@app.route('/api/v1/match/generate', methods=['POST'])
@require_api_admin
def api_generate_matches_endpoint():
    data = request.get_json()
    p = data.get('phase')
    g = data.get('group')
    if not p or not g: return api_response(False, message="args error", code=400)
    
    cnt, msg = auto_create_matches(p, g)
    return api_response(True, message=f"{msg} ({cnt}场)")

@app.route('/api/v1/player/<int:player_id>/match', methods=['GET'])
def api_player_match_info(player_id):
    p = Player.query.get(player_id)
    if not p: return api_response(False, message="404", code=404)
    
    m = get_active_match(p.id)
    if not m: return api_response(True, data=None)
    
    op_id = m.player2_id if m.player1_id == p.id else m.player1_id
    op = Player.query.get(op_id)
    
    # Peak Logic
    mysel = SongSelection.query.filter_by(match_id=m.id, player_id=p.id, is_banned=False).first()
    opsel = SongSelection.query.filter_by(match_id=m.id, player_id=op_id, is_banned=False).first()
    
    ban_rec = SongSelection.query.filter_by(match_id=m.id, banned_by_id=p.id).first()
    was_banned = SongSelection.query.filter_by(match_id=m.id, player_id=p.id, is_banned=True).count() > 0

    # Reveal Logic: Only show opponent selection if ALL players in this phase/group have submitted
    reveal_op = False
    
    # 判断是否为自选曲阶段
    is_selection_phase = False
    group_config = TOURNAMENT_CONFIG['groups'].get(m.group)
    if group_config and m.phase in group_config.get('self_selection_phases', []):
        is_selection_phase = True

    if is_selection_phase and opsel:
        # 1. Get all matches in this phase/group
        phase_matches = Match.query.filter_by(phase=m.phase, group=m.group).all()
        # 2. Get all player IDs involved
        p_ids = set()
        for pm in phase_matches:
            p_ids.add(pm.player1_id)
            p_ids.add(pm.player2_id)
        
        # 3. Count valid selections for these players
        # Note: We need to ensure *every* player has a valid (non-banned) selection
        sel_count = SongSelection.query.filter(
            SongSelection.player_id.in_(p_ids),
            SongSelection.is_banned == False
        ).count()
        
        if sel_count >= len(p_ids):
            reveal_op = True
    
    # Hide op selection if not revealed
    op_data = None
    if opsel:
        if reveal_op:
            op_data = {"song_name": opsel.song_name, "difficulty": opsel.difficulty}
        else:
            op_data = {"song_name": "Hidden (Waiting for all)", "difficulty": 0, "hidden": True}

    return api_response(True, data={
        "match_id": m.id,
        "phase": m.phase,
        "group": m.group,
        "opponent": {"id": op.id, "name": op.name, "rating": op.rating, "forfeited": op.forfeited},
        "my_selection": {"song_name": mysel.song_name, "difficulty": mysel.difficulty} if mysel else None,
        "op_selection": op_data,
        "ban_used": p.ban_used,
        "has_banned_this_match": (ban_rec is not None),
        "was_banned": was_banned,
        "is_selection_phase": is_selection_phase
    })

@app.route('/api/v1/player/<int:player_id>/match/submit_song', methods=['POST'])
def api_match_submit_song(player_id):
    # 通用自选曲提交接口 (Configurable)
    
    p = Player.query.get(player_id)
    m = get_active_match(player_id)
    if not m: return api_response(False, message="当前无进行中比赛", code=400)
    
    # 验证是否允许自选曲
    allowed = False
    group_config = TOURNAMENT_CONFIG['groups'].get(m.group)
    if group_config and m.phase in group_config.get('self_selection_phases', []):
        allowed = True
        
    if not allowed:
        return api_response(False, message="当前阶段不支持自选曲", code=400)
    
    data = request.get_json()
    name = data.get('song_name')
    diff = data.get('difficulty')
    
    if not name or diff is None:
        return api_response(False, message="参数不完整", code=400)

    try:
        diff = int(diff)
    except (ValueError, TypeError):
        return api_response(False, message="难度等级必须是数字", code=400)
    
    # check existing
    exist = SongSelection.query.filter_by(match_id=m.id, player_id=p.id, is_banned=False).first()
    if exist: return api_response(False, message="您已提交过自选曲", code=400)
    
    db.session.add(SongSelection(match_id=m.id, player_id=p.id, song_name=name, difficulty=diff))
    db.session.commit()
    return api_response(True, message="提交成功")

@app.route('/api/v1/player/<int:player_id>/peak/ban_song', methods=['POST'])
def api_peak_ban(player_id):
    p = Player.query.get(player_id)
    if p.ban_used: return api_response(False, message="Ban used", code=400)
    
    m = get_active_match(player_id)
    if not m: return api_response(False, message="No match", code=400)
    
    op_id = m.player2_id if m.player1_id == p.id else m.player1_id
    opsel = SongSelection.query.filter_by(match_id=m.id, player_id=op_id, is_banned=False).first()
    if not opsel: return api_response(False, message="No target song", code=400)
    
    opsel.is_banned = True
    opsel.banned_by_id = p.id
    p.ban_used = True
    db.session.commit()
    return api_response(True, message="Banned")


@app.route('/api/v1/peak/matches_overview', methods=['GET'])
def api_peak_matches_overview():
    """
    巅峰组选曲概览：用于后台展示每个对局的双方自选曲目
    Query: phase=top4|final
    """
    phase = request.args.get('phase', 'top4')
    if phase not in ['top4', 'final']:
        return api_response(False, message='phase 必须为 top4 或 final', code=400)
    
    matches = Match.query.filter_by(group='peak', phase=phase).all()
    if not matches:
        return api_response(True, data={
            'phase': phase,
            'reveal_ready': False,
            'matches': []
        })
    
    # 收集所有参赛选手 ID
    p_ids = set()
    for m in matches:
        p_ids.add(m.player1_id)
        p_ids.add(m.player2_id)
    
    # 判断是否达到公开条件（所有选手均已提交有效选曲）
    sel_count = SongSelection.query.filter(
        SongSelection.player_id.in_(p_ids),
        SongSelection.is_banned == False
    ).count()
    reveal_ready = (sel_count >= len(p_ids))
    
    payload = []
    for m in matches:
        p1 = Player.query.get(m.player1_id)
        p2 = Player.query.get(m.player2_id)
        s1 = SongSelection.query.filter_by(match_id=m.id, player_id=m.player1_id, is_banned=False).first()
        s2 = SongSelection.query.filter_by(match_id=m.id, player_id=m.player2_id, is_banned=False).first()
        payload.append({
            'match_id': m.id,
            'status': m.status,
            'player1': {'id': p1.id, 'name': p1.name, 'rating': p1.rating},
            'player2': {'id': p2.id, 'name': p2.name, 'rating': p2.rating},
            'selection1': ({'song_name': s1.song_name, 'difficulty': s1.difficulty} if s1 else None),
            'selection2': ({'song_name': s2.song_name, 'difficulty': s2.difficulty} if s2 else None)
        })
    
    return api_response(True, data={
        'phase': phase,
        'reveal_ready': reveal_ready,
        'matches': payload
    })



@app.route('/api/v1/admin/start_match', methods=['POST'])
@require_api_admin
def api_admin_start_match():
    """开始比赛，开启 1 小时倒计时"""
    state = get_system_state()
    if state.match_started:
        return api_response(False, message='比赛已经开始', code=400)
    
    state.match_started = True
    state.checkin_enabled = True # 确保签到开启
    state.start_time = datetime.utcnow()
    db.session.commit()
    
    return api_response(True, message='比赛已开始，倒计时启动')


@app.route('/api/v1/admin/enable_checkin', methods=['POST'])
@require_api_admin
def api_admin_enable_checkin():
    """手动开启签到（不开始比赛/倒计时）"""
    state = get_system_state()
    if state.checkin_enabled:
        return api_response(True, message='签到已开启')
    
    state.checkin_enabled = True
    db.session.commit()
    return api_response(True, message='签到通道已开启')


@app.route('/api/v1/admin/generate_numbers', methods=['POST'])
@require_api_admin
def api_admin_generate_numbers():
    state = get_system_state()
    if state.match_generated:
        return api_response(False, message='当前操作已锁定，请先解锁', code=400)
    try:
        beginner_players = Player.query.filter_by(checked_in=True, group='beginner').all()
        advanced_players = Player.query.filter_by(checked_in=True, group='advanced').all()
        total_numbered = 0
        if beginner_players:
            random.shuffle(beginner_players)
            nums = list(range(1, len(beginner_players) + 1))
            for p, num in zip(beginner_players, nums):
                p.match_number = num
            total_numbered += len(beginner_players)
        if advanced_players:
            random.shuffle(advanced_players)
            nums = list(range(1, len(advanced_players) + 1))
            for p, num in zip(advanced_players, nums):
                p.match_number = num
            total_numbered += len(advanced_players)
        state.match_generated = True
        db.session.commit()
        return api_response(True, message=f'成功为 {total_numbered} 名已签到选手分配了随机序号')
    except Exception as e:
        db.session.rollback()
        return api_response(False, message=str(e), code=500)


@app.route('/api/v1/admin/unlock_generate', methods=['POST'])
@require_api_admin
def api_admin_unlock_generate():
    data = request.get_json() or {}
    pwd = (data.get('password') or '').strip()
    if pwd != '1145141919810ax':
        return api_response(False, message='解锁密码错误', code=400)
    state = get_system_state()
    state.match_generated = False
    db.session.commit()
    return api_response(True, message='已解锁随机分配操作')


@app.route('/api/v1/admin/create_matches', methods=['POST'])
@require_api_admin
def api_admin_create_matches():
    data = request.get_json() or {}
    phase = (data.get('phase') or '').strip()
    group = (data.get('group') or '').strip()
    if not phase or not group:
        return api_response(False, message='请提供赛程与组别', code=400)
    try:
        count, msg = auto_create_matches(phase, group)
        if count > 0:
            return api_response(True, message=msg)
        else:
            return api_response(False, message=msg, code=400)
    except Exception as e:
        return api_response(False, message=str(e), code=500)


@app.route('/api/v1/admin/clear_all_secure', methods=['POST'])
@require_api_admin
def api_admin_clear_all_secure():
    data = request.get_json() or {}
    pwd = (data.get('password') or '').strip()
    if pwd != '1145141919810ax':
        return api_response(False, message='清除数据密码错误', code=400)
    try:
        deleted = Player.query.delete()
        state = get_system_state()
        state.match_generated = False
        db.session.commit()
        return api_response(True, message=f'已清除所有选手数据（共 {deleted} 条），并重置系统')
    except Exception as e:
        db.session.rollback()
        return api_response(False, message=str(e), code=500)


@app.route('/api/v1/admin/test_start', methods=['POST'])
@require_api_admin
def api_admin_test_start():
    data = request.get_json() or {}
    pwd = (data.get('password') or '').strip()
    if pwd != '1145141919810ax':
        return api_response(False, message='密码错误', code=400)
    state = get_system_state()
    state.checkin_enabled = True
    state.match_started = True
    state.start_time = None
    state.checkin_timeout_processed = False
    db.session.commit()
    return api_response(True, message='测试模式开启：比赛开始且不启用倒计时')


@app.route('/api/v1/admin/import_players', methods=['POST'])
@require_api_admin
def api_admin_import_players():
    data = request.get_json() or {}
    entries = data.get('entries') or []
    if not isinstance(entries, list) or not entries:
        return api_response(False, message='请提供有效的选手列表', code=400)
    added = 0
    for item in entries:
        name = (item.get('name') or '').strip()
        rating = int(item.get('rating') or 0)
        group = (item.get('group') or 'beginner').strip()
        if not name:
            continue
        if not Player.query.filter_by(name=name).first():
            db.session.add(Player(name=name, rating=rating, group=group))
            added += 1
    db.session.commit()
    return api_response(True, message=f'成功添加 {added} 名选手')

@app.route('/api/v1/admin/trigger_timeout', methods=['POST'])
@require_api_admin
def api_admin_trigger_timeout():
    """触发超时未签到处理 (Admin FE 倒计时结束时调用)"""
    state = get_system_state()
    if not state.match_started:
        return api_response(False, message='比赛尚未开始', code=400)
    if state.checkin_timeout_processed:
        return api_response(False, message='超时处理已执行过', code=400)
    
    # 检查时间 (服务端校验: 必须超过 1 小时)
    # 为了避免边界误差，允许 59 分钟以上
    elapsed = datetime.utcnow() - state.start_time
    if elapsed.total_seconds() < 3540: # 59 mins
        return api_response(False, message='倒计时未结束', code=400)
        
    # 处理未签到选手
    # 逻辑：未签到 (checked_in=False) -> 标记为 timeout_eliminated
    # 且 forfeited=True (为了不参与后续匹配)
    
    players = Player.query.filter(
        Player.checked_in == False,
        Player.forfeited == False
    ).all()
    
    count = 0
    for p in players:
        p.forfeited = True
        p.promotion_status = 'timeout_eliminated'
        count += 1
        
    state.checkin_timeout_processed = True
    db.session.commit()
    
    return api_response(True, message=f'已处理超时未签到选手，共 {count} 人被取消资格')


@app.route('/api/v1/admin/players_all', methods=['GET'])
@require_api_admin
def api_admin_players_all():
    players = Player.query.all()
    data = []
    for p in players:
        data.append({
            'id': p.id,
            'name': p.name,
            'checked_in': p.checked_in,
            'match_number': p.match_number,
            'group': p.group,
            'on_machine': p.on_machine,
            'promotion_status': p.promotion_status,
            'rating': p.rating,
            'score_round1': p.score_round1,
            'score_revival': p.score_revival
        })
    return api_response(True, data=data)


@app.route('/api/v1/admin/update_players', methods=['POST'])
@require_api_admin
def api_admin_update_players():
    data = request.get_json() or {}
    players_data = data.get('players') or []
    if not players_data:
        return api_response(False, message='没有提供选手数据', code=400)
    
    count = 0
    try:
        for p_data in players_data:
            pid = p_data.get('id')
            if not pid: continue
            
            p = Player.query.get(pid)
            if not p: continue
            
            # Update fields
            if 'group' in p_data: p.group = p_data['group']
            if 'match_number' in p_data: p.match_number = p_data['match_number']
            if 'score_round1' in p_data: p.score_round1 = p_data['score_round1']
            if 'score_revival' in p_data: p.score_revival = p_data['score_revival']
            if 'promotion_status' in p_data: p.promotion_status = p_data['promotion_status']
            
            count += 1
        
        db.session.commit()
        return api_response(True, message=f'成功更新 {count} 名选手')
    except Exception as e:
        db.session.rollback()
        return api_response(False, message=str(e), code=500)


@app.route('/api/v1/admin/delete_player_api', methods=['POST'])
@require_api_admin
def api_admin_delete_player_api():
    data = request.get_json() or {}
    pid = data.get('player_id')
    if not pid:
        return api_response(False, message='缺少 player_id', code=400)
    
    p = Player.query.get(pid)
    if not p:
        return api_response(False, message='选手不存在', code=404)
        
    try:
        db.session.delete(p)
        db.session.commit()
        return api_response(True, message='删除成功')
    except Exception as e:
        db.session.rollback()
        return api_response(False, message=str(e), code=500)


@app.route('/api/v1/admin/add_song_simple', methods=['POST'])
@require_api_admin
def api_admin_add_song_simple():
    data = request.get_json() or {}
    phase = data.get('phase')
    group = data.get('group')
    name = data.get('name')
    
    if not phase or not group or not name:
        return api_response(False, message='缺少必要参数', code=400)
        
    try:
        # Check duplicate
        exists = Song.query.filter_by(phase=phase, group=group, name=name).first()
        if exists:
             return api_response(False, message='曲目已存在', code=400)
             
        song = Song(phase=phase, group=group, name=name)
        db.session.add(song)
        db.session.commit()
        return api_response(True, message='添加成功')
    except Exception as e:
        db.session.rollback()
        return api_response(False, message=str(e), code=500)


@app.route('/api/v1/admin/songs_all', methods=['GET'])
@require_api_admin
def api_admin_songs_all():
    songs = Song.query.all()
    data = []
    for s in songs:
        data.append({
            'id': s.id,
            'name': s.name,
            'phase': s.phase,
            'group': s.group,
            # 'image_filename': s.image_filename # Song model might not have image_filename in app.py snippet I saw earlier, need to verify
            # Wait, I didn't verify Song model fields. admin.html uses s.name.
            # Let's assume name, phase, group.
        })
    return api_response(True, data=data)


@app.route('/api/v1/system/state', methods=['GET'])
def api_system_state():
    """获取系统状态 (Web/App 轮询用)"""
    state = get_system_state()
    
    # Calculate remaining seconds
    remaining = -1 # Default to -1 (no countdown / unlimited)
    if state.match_started:
        if state.start_time:
            elapsed = (datetime.utcnow() - state.start_time).total_seconds()
            remaining = max(0, 3600 - int(elapsed))
        else:
            # start_time is None but match_started is True => Test Mode / No Countdown
            remaining = -1
        
    return api_response(True, data={
        'match_started': state.match_started,
        'checkin_enabled': state.checkin_enabled,
        'timeout_processed': state.checkin_timeout_processed,
        'remaining_seconds': remaining
    })


@app.route('/launch_app')
def launch_app():
    """尝试通过 Intent 唤起 App，失败则跳转首页"""
    return render_template('launch_app.html')


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)

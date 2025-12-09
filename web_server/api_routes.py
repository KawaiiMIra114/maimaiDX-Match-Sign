# ================= REST API 接口（供 Android App 调用） =================
#
# 所有 API 统一前缀 /api/v1/
# 返回格式：JSON
# 认证方式：管理端操作需要在 header 中传递 X-Admin-Token（或使用 session）
#

from functools import wraps


def api_response(success=True, data=None, message=None, code=200):
    """统一的 API 响应格式"""
    response = {
        'success': success,
        'code': code,
    }
    if data is not None:
        response['data'] = data
    if message is not None:
        response['message'] = message
    return jsonify(response), code


def require_api_admin(f):
    """API 管理员认证装饰器"""
    @wraps(f)
    def decorated_function(*args, **kwargs):
        # 支持 Session 或 Header Token 认证
        if session.get('is_admin'):
            return f(*args, **kwargs)
        token = request.headers.get('X-Admin-Token')
        # 建议使用环境变量配置 Token
        expected_token = os.environ.get('ADMIN_API_TOKEN', 'admin_token_2024')
        
        if token == expected_token:
            return f(*args, **kwargs)
        return api_response(False, message='需要管理员权限', code=401)
    return decorated_function


# ============ 选手端 API ============

@app.route('/api/v1/player/checkin', methods=['POST'])
def api_player_checkin():
    """
    选手签到
    POST body: { "name": "选手姓名" }
    返回: 选手信息
    """
    data = request.get_json()
    if not data or not data.get('name'):
        return api_response(False, message='请提供选手姓名', code=400)
    
    name = data['name'].strip()
    player = Player.query.filter_by(name=name).first()
    
    if not player:
        return api_response(False, message=f'未找到选手 "{name}"，请联系工作人员', code=404)
    
    # 执行签到
    if not player.checked_in:
        player.checked_in = True
        
        # 分配比赛序号
        max_num = db.session.query(func.max(Player.match_number)).filter(
            Player.group == player.group,
            Player.checked_in == True
        ).scalar() or 0
        player.match_number = max_num + 1
        
        db.session.commit()
    
    return api_response(True, data={
        'id': player.id,
        'name': player.name,
        'group': player.group,
        'group_label': '萌新组' if player.group == 'beginner' else '进阶组',
        'match_number': player.match_number,
        'checked_in': player.checked_in,
        'on_machine': player.on_machine,
        'promotion_status': player.promotion_status,
        'rating': player.rating
    }, message='签到成功')


@app.route('/api/v1/player/<int:player_id>', methods=['GET'])
def api_get_player(player_id):
    """获取选手信息"""
    player = Player.query.get(player_id)
    if not player:
        return api_response(False, message='选手不存在', code=404)
    
    return api_response(True, data={
        'id': player.id,
        'name': player.name,
        'group': player.group,
        'group_label': '萌新组' if player.group == 'beginner' else '进阶组',
        'match_number': player.match_number,
        'checked_in': player.checked_in,
        'on_machine': player.on_machine,
        'promotion_status': player.promotion_status,
        'rating': player.rating,
        'score_round1': player.score_round1,
        'score_round2': player.score_round2,
        'score_revival': player.score_revival
    })


@app.route('/api/v1/player/<int:player_id>/toggle_machine', methods=['POST'])
def api_toggle_machine(player_id):
    """切换选手上机状态"""
    player = Player.query.get(player_id)
    if not player:
        return api_response(False, message='选手不存在', code=404)
    
    if not player.checked_in:
        return api_response(False, message='请先签到', code=400)
    
    # 切换状态
    if player.on_machine:
        player.on_machine = False
        msg = '已标记下机'
    else:
        # 检查同组是否已有人在机
        same_group_on = Player.query.filter(
            Player.group == player.group,
            Player.on_machine == True,
            Player.id != player.id
        ).first()
        if same_group_on:
            return api_response(False, message=f'同组选手 {same_group_on.name} 正在比赛中', code=400)
        player.on_machine = True
        msg = '已标记上机'
    
    db.session.commit()
    return api_response(True, data={'on_machine': player.on_machine}, message=msg)


@app.route('/api/v1/player/<int:player_id>/submit_score', methods=['POST'])
def api_submit_score(player_id):
    """选手提交成绩"""
    player = Player.query.get(player_id)
    if not player:
        return api_response(False, message='选手不存在', code=404)
    
    data = request.get_json()
    round_type = data.get('round', 'round1')  # round1 / round2 / revival
    score = data.get('score')
    
    if score is None:
        return api_response(False, message='请提供成绩', code=400)
    
    try:
        score = float(score)
    except:
        return api_response(False, message='成绩格式错误', code=400)
    
    if round_type == 'round1':
        player.score_round1 = score
    elif round_type == 'round2':
        player.score_round2 = score
    elif round_type == 'revival':
        player.score_revival = score
    else:
        return api_response(False, message='未知的赛程类型', code=400)
    
    player.on_machine = False
    db.session.commit()
    
    return api_response(True, message='成绩提交成功')


# ============ 仪表盘统计 API ============

@app.route('/api/v1/dashboard', methods=['GET'])
def api_dashboard():
    """获取仪表盘统计信息"""
    stats = get_dashboard_stats()
    state = get_system_state()
    
    return api_response(True, data={
        'total_players': stats['total_players'],
        'checked_in': stats['checked_in'],
        'beginner_count': stats['beginner_count'],
        'advanced_count': stats['advanced_count'],
        'top16_beginner': stats['top16_beginner'],
        'top16_advanced': stats['top16_advanced'],
        'revival_beginner': stats['revival_beginner'],
        'revival_advanced': stats['revival_advanced'],
        'match_generated': state.match_generated,
        'max_beginner_number': db.session.query(func.max(Player.match_number)).filter(
            Player.group == 'beginner', Player.checked_in == True
        ).scalar() or 0,
        'max_advanced_number': db.session.query(func.max(Player.match_number)).filter(
            Player.group == 'advanced', Player.checked_in == True
        ).scalar() or 0
    })


# ============ 选手列表 API ============

@app.route('/api/v1/players', methods=['GET'])
def api_list_players():
    """
    获取选手列表
    Query params:
    - group: beginner / advanced (可选)
    - checked_in: true / false (可选)
    - sort: name / match_number / rating / score (可选)
    - search: 姓名搜索 (可选)
    """
    query = Player.query
    
    # 过滤条件
    group = request.args.get('group')
    if group:
        query = query.filter(Player.group == group)
    
    checked_in = request.args.get('checked_in')
    if checked_in == 'true':
        query = query.filter(Player.checked_in == True)
    elif checked_in == 'false':
        query = query.filter(Player.checked_in == False)
    
    search = request.args.get('search')
    if search:
        query = query.filter(Player.name.contains(search))
    
    # 排序
    sort = request.args.get('sort', 'name')
    if sort == 'match_number':
        query = query.order_by(Player.match_number.asc())
    elif sort == 'rating':
        query = query.order_by(Player.rating.desc())
    elif sort == 'score':
        query = query.order_by(Player.score_round1.desc())
    else:
        query = query.order_by(Player.name.asc())
    
    players = query.all()
    
    return api_response(True, data=[{
        'id': p.id,
        'name': p.name,
        'group': p.group,
        'group_label': '萌新组' if p.group == 'beginner' else '进阶组',
        'match_number': p.match_number,
        'checked_in': p.checked_in,
        'on_machine': p.on_machine,
        'promotion_status': p.promotion_status,
        'rating': p.rating,
        'score_round1': p.score_round1
    } for p in players])


# ============ 管理端 API ============

@app.route('/api/v1/admin/login', methods=['POST'])
def api_admin_login():
    """管理员登录"""
    data = request.get_json()
    password = data.get('password', '')
    
    if password == 'harbin2024':  # 与网页端密码一致
        session['is_admin'] = True
        return api_response(True, data={'token': 'harbin_red_chart_2024'}, message='登录成功')
    
    return api_response(False, message='密码错误', code=401)


@app.route('/api/v1/admin/player', methods=['POST'])
@require_api_admin
def api_add_player():
    """添加选手"""
    data = request.get_json()
    name = data.get('name', '').strip()
    group = data.get('group', 'beginner')
    rating = data.get('rating', 0)
    
    if not name:
        return api_response(False, message='选手姓名不能为空', code=400)
    
    existing = Player.query.filter_by(name=name).first()
    if existing:
        return api_response(False, message=f'选手 "{name}" 已存在', code=400)
    
    player = Player(name=name, group=group, rating=rating)
    db.session.add(player)
    db.session.commit()
    
    return api_response(True, data={'id': player.id}, message='添加成功')


@app.route('/api/v1/admin/player/<int:player_id>', methods=['PUT'])
@require_api_admin
def api_update_player(player_id):
    """更新选手信息"""
    player = Player.query.get(player_id)
    if not player:
        return api_response(False, message='选手不存在', code=404)
    
    data = request.get_json()
    
    if 'name' in data:
        player.name = data['name'].strip()
    if 'group' in data:
        player.group = data['group']
    if 'rating' in data:
        player.rating = data['rating']
    if 'match_number' in data:
        player.match_number = data['match_number']
    if 'promotion_status' in data:
        player.promotion_status = data['promotion_status']
    if 'score_round1' in data:
        player.score_round1 = data['score_round1']
    
    db.session.commit()
    return api_response(True, message='更新成功')


@app.route('/api/v1/admin/player/<int:player_id>', methods=['DELETE'])
@require_api_admin
def api_delete_player(player_id):
    """删除选手"""
    player = Player.query.get(player_id)
    if not player:
        return api_response(False, message='选手不存在', code=404)
    
    db.session.delete(player)
    db.session.commit()
    return api_response(True, message='删除成功')


@app.route('/api/v1/admin/generate_numbers', methods=['POST'])
@require_api_admin
def api_generate_numbers():
    """结束签到并生成随机序号"""
    state = get_system_state()
    
    # 检查密码（如果已经生成过）
    if state.match_generated:
        data = request.get_json()
        password = data.get('password', '')
        if password != 'taiko':
            return api_response(False, message='需要密码解锁', code=403)
    
    # 为每个组别的已签到选手生成随机序号
    for grp in ['beginner', 'advanced']:
        players = Player.query.filter_by(group=grp, checked_in=True).all()
        random.shuffle(players)
        for idx, p in enumerate(players, 1):
            p.match_number = idx
    
    state.match_generated = True
    db.session.commit()
    
    return api_response(True, message='随机序号生成成功')


@app.route('/api/v1/admin/promote_16', methods=['POST'])
@require_api_admin
def api_promote_16():
    """按海选成绩生成 16 强和复活赛"""
    for grp in ['beginner', 'advanced']:
        players = Player.query.filter(
            Player.group == grp,
            Player.checked_in == True,
            Player.score_round1 != None
        ).order_by(Player.score_round1.desc()).all()
        
        for i, p in enumerate(players):
            if i < 16:
                p.promotion_status = 'top16'
            elif i < 24:
                p.promotion_status = 'revival'
            else:
                p.promotion_status = 'eliminated'
    
    db.session.commit()
    return api_response(True, message='16 强和复活赛名单已生成')


# ============ 曲目抽选 API ============

@app.route('/api/v1/songs', methods=['GET'])
def api_list_songs():
    """
    获取曲目列表
    Query params:
    - phase: qualifier / revival / semifinal / final
    - group: beginner / advanced
    """
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


@app.route('/api/v1/song_draw/state', methods=['GET'])
def api_song_draw_state():
    """获取当前抽选状态"""
    draw_state = get_song_draw_state()
    selected_songs = draw_state.get_selected_songs()
    
    return api_response(True, data={
        'status': draw_state.status,
        'phase': draw_state.phase,
        'group': draw_state.group,
        'selected_songs': [{
            'id': s.id,
            'name': s.name,
            'image_url': f'/static/songs/{s.image_filename}' if s.image_filename else None
        } for s in selected_songs] if selected_songs else []
    })


@app.route('/api/v1/song_draw/control', methods=['POST'])
@require_api_admin
def api_song_draw_control():
    """控制抽选（开始/停止）"""
    data = request.get_json()
    action = data.get('action')  # start / stop
    phase = data.get('phase')
    group = data.get('group')
    
    draw_state = get_song_draw_state()
    
    if action == 'start':
        if not phase or not group:
            return api_response(False, message='请指定赛程和组别', code=400)
        
        draw_state.status = 'rolling'
        draw_state.phase = phase
        draw_state.group = group
        draw_state.selected_song_ids = None
        db.session.commit()
        
        return api_response(True, message='抽选已开始')
    
    elif action == 'stop':
        # 随机选择 1-2 首曲目
        songs = Song.query.filter(
            Song.phase == draw_state.phase,
            Song.group == draw_state.group,
            Song.active == True
        ).all()
        
        if not songs:
            return api_response(False, message='没有可用曲目', code=400)
        
        count = 2 if len(songs) >= 2 else 1
        selected = random.sample(songs, count)
        
        draw_state.status = 'finished'
        draw_state.set_selected_songs(selected)
        db.session.commit()
        
        return api_response(True, data={
            'selected_songs': [{'id': s.id, 'name': s.name} for s in selected]
        }, message='抽选完成')
    
    elif action == 'reset':
        draw_state.status = 'idle'
        draw_state.selected_song_ids = None
        db.session.commit()
        return api_response(True, message='已重置')
    
    return api_response(False, message='未知操作', code=400)


# ============ 系统信息 API ============

@app.route('/api/v1/system/info', methods=['GET'])
def api_system_info():
    """获取系统基本信息"""
    state = get_system_state()
    return api_response(True, data={
        'app_name': '术力口大赛签到系统',
        'version': '1.0.0',
        'match_generated': state.match_generated
    })

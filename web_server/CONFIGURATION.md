# 赛制配置指南 (Tournament Configuration Guide)

本项目的 `web_server/app.py` 文件顶部包含了一个 `TOURNAMENT_CONFIG` 配置项，允许您自定义赛制规则、晋级流程和自选曲阶段。

## 配置文件位置

打开 `web_server/app.py`，在文件顶部（导入语句之后）可以找到如下配置：

```python
TOURNAMENT_CONFIG = {
    'groups': {
        'beginner': {
            'label': '萌新组',
            # 海选晋级规则
            'qualifier_promotion': [
                {'status': 'top16', 'count': 15}, 
                {'status': 'revival', 'count': 4}
            ],
            # 允许自选曲的阶段
            'self_selection_phases': ['top16', 'top8', 'top4', 'final']
        },
        # ... 其他组别
    }
}
```

## 如何修改

### 1. 修改晋级规则 (qualifier_promotion)

`qualifier_promotion` 是一个列表，定义了海选结束后，选手根据成绩排名依次获得的“状态” (promotion_status)。

*   **status**: 晋级后的状态名称 (如 `top16`, `revival`, `top8`)。
*   **count**: 该状态容纳的人数。

**示例：**
如果您希望萌新组直接晋级 32 人进入 32 强，没有复活赛：

```python
'qualifier_promotion': [
    {'status': 'top32', 'count': 32}
]
```

**注意：** 修改状态名称后，您可能需要同步修改前端显示逻辑或后续的对阵生成逻辑（目前对阵生成主要依赖 `phase` 参数，通常 phase 名称与 status 名称一致）。

### 2. 修改自选曲阶段 (self_selection_phases)

`self_selection_phases` 定义了哪些阶段允许选手在前端提交自选曲目。

*   列表中包含的阶段 (phase)，选手在登录后会看到“自选曲目”面板。
*   如果不包含，则该阶段仅显示对阵信息，不显示选曲提交入口。

**示例：**
如果您希望萌新组只在决赛 (final) 允许自选曲，其他阶段由系统指定：

```python
'self_selection_phases': ['final']
```

### 3. 修改组别名称

您可以修改 `'label'` 字段来改变前端显示的组别名称。但请注意不要修改 `'beginner'`, `'advanced'`, `'peak'` 这些键名 (key)，因为数据库和代码逻辑中大量使用了这些键名。

## 常见问题

*   **修改后不生效？**
    请重启 web server (`python app.py`) 以应用更改。

*   **如何添加新的组别？**
    目前代码逻辑对 `beginner`, `advanced`, `peak` 有一定程度的硬编码（如 API 路由参数校验），添加全新组别需要修改 `app.py` 中的多处逻辑，不建议仅通过配置文件添加。

*   **Ban 卡机制**
    本版本默认仅在 `peak` (巅峰组) 开启 Ban 卡功能。如果您希望其他组别也开启 Ban 卡，需要修改前端 `index.html` 中的判断逻辑。

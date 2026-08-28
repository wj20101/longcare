## MODIFIED Requirements

### Requirement: 已知阻断与新增回退分开分类
项目健康评估 SHALL 将新增失败、项目可控且预期 fail-closed 的风险、已明确接受且由外部厂商负责的非阻断风险、不适用项和工具或环境失败分别分类，同时保留每项风险的原始严重度、责任边界与复核条件。

#### Scenario: 生产检查命中项目可控的 QLZ 配置问题
- **WHEN** production 检查发现固定测试配置、测试模式、缺失必需 AAR 或项目侧 fallback
- **THEN** 结果 SHALL 标为“已知风险且预期 fail-closed”，而不是可忽略通过

#### Scenario: 检查命中已接受的 QLZ 厂商内部风险
- **WHEN** 当前批准的 QLZ AAR 命中已登记且只能由厂商处理的内部 finding
- **THEN** 结果 SHALL 保留严重度并标为“厂商所有的已知外部风险、已接受且非项目发布阻断”，不得仅凭该 finding 判定 production 失败

#### Scenario: 生产发布命中已知厂商阻断
- **WHEN** 腾讯人脸或其他厂商问题仍被当前发布策略定义为 release-required blocker
- **THEN** 结果 SHALL 继续标为“已知风险且预期 fail-closed”，不得由 QLZ 风险接受决定连带放宽

#### Scenario: 基线外出现新失败
- **WHEN** 某项检查出现当前真相文档未覆盖且可复现的失败
- **THEN** 结果 SHALL 标为新增发现，并记录影响、复现条件、严重度、责任边界和建议的独立验证路径

# 助手 Launcher 图标

图标通过内置 image_gen 生成（非 CLI/API 模式），参考主应用 `app_logo_round.webp` 的白色无限环与分段圆弧，以深蓝扳手徽章区分内部验证助手。主应用原图未修改。

运行时位图：[assistant_brand.png](../src/main/res/drawable-xxhdpi/assistant_brand.png)。生成结果等比缩至 432×432，按项目约定统一放在 `drawable-xxhdpi`；显示范围仍由图标 XML 控制。Android 自适应前景使用 16% inset，使徽章也处于安全区域。API 24 使用位图回退，API 26 起使用前景/背景层，API 33 起使用相同标识语义的专用单色矢量层。

最终生成提示词（输入图为第一轮助手图标草稿）：

> Use case: logo-brand. Edit target Image 1: the LongCare assistant icon draft. Keep its white infinity loop with separate enclosing circular arcs and the small navy circle/white wrench companion badge. Redraw as perfectly clean flat vector-style geometry: remove ALL speckles, rough texture, noise and grunge. Use a completely solid bright azure blue #1688F8 background across the entire square canvas, not transparency. Pure white emblem, solid navy badge, no gradients or shadows or text. No rounded square tile outline or pre-masking. Keep all white emblem and navy badge artwork within the CENTERED 60% DIAMETER circle of the canvas to fit Android adaptive icon safety limits; generous uniform blue margin around the artwork. Single square production icon, not a mockup.

后续替换时应同时维护位图、安全区 inset 和单色资源，并运行 `AssistantIconTest`；不要只替换 Manifest 的图标引用。

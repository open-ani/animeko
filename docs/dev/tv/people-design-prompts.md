# TV 角色与制作人员页面设计图提示词

使用内置 imagegen 生成两张设计预览。共同参考：`build/reports/tv-actions-glass/overview.jpg`，仅用于应用视觉风格。图片、文案和计数为设计占位。

## 角色页

```text
Use case: ui-mockup
Create one polished, production-believable Android TV CHARACTER detail page, as a flat full-screen UI screenshot, 1920x1080, exactly 16:9. This is a NEW page design. The provided image is only a visual style reference for Animeko's existing TV subject page, not an edit target. Match its elegant large regular-weight white typography, deep translucent rounded cards, generous margins, quiet visual hierarchy and white focus ring. All visible UI text is Simplified Chinese except the original Japanese name.

This page represents fictional sample data for the known anime character Agnes Tachyon, solely to demonstrate layout. Full-screen very dark warm brown/charcoal background with extremely defocused colors derived from the portrait, dark left gradient; no sharp full-screen anime poster. Preserve subtle tonal depth.
Use a 960x540 dp design grid rendered at 2x. Horizontal safe margins 58dp. Left content runs x=58..600dp. Right portrait x=688..902dp, y=66..344dp: one ordinary 3:4 rectangular portrait image with 22dp rounded corners, depicting a brown-haired horse-eared anime researcher in a white lab coat, cropped carefully to head and torso; preserve an actual photo-card background, NOT a transparent cutout and NOT giant face art.
At x58 y42 small muted eyebrow "角色".
At x58 y68 big 40sp regular heading "爱丽速子".
At x58 y120 muted 19sp "アグネスタキオン".
At x58 y159 one 15sp metadata row "角色  ·  1,284 人收藏  ·  8 部出演作品". This is metadata, no star ratings, no favorite toggle.

At x58 y205 place a 256x116dp rounded translucent card with 26dp corners. It is the currently focused card: 2.5dp white outline, slightly brighter surface, top-right small WHITE circle with BLACK right arrow. Heading 18sp "角色介绍"; text 15sp, two or three lines: "不断探索速度极限的赛马娘，对未知的可能性充满好奇。" Bottom-right small underlined "更多".
At x332 y205 place a 256x116dp matching UNFOCUSED card with subtle thin grey border, title "讨论 · 128", small subdued arrow at top right. Preview text "这个角色的台词和声线，真的让人过目不忘。" Small muted caption "来自用户讨论". NO generated consensus or numerical review score.
At x58 y343 two 40dp-high frosted glass capsule actions: "浏览出演作品" with simple grid icon, then "查看大图" with simple expand icon. These are unfocused, modest buttons. No play action, no Follow, no Rate, no external browser call-to-action here.
At x58 y418 heading "配音演员". At y459 a horizontally scrolling row of circular portraits. Show a single real-sized circle 92dp wide with a tasteful generic studio portrait of a Japanese female voice actress, label "上坂堇" under it (can be just below the screen crop). This demonstrates that a one-person cast does NOT create fake extra avatars or placeholders. A small part of the next vertical content is suggested by cropping at bottom. Do not fill empty row space with invented actors.
No browser chrome, no device frame, no sidebar, no back button, no bottom remote instructions, no watermarks, no explanation arrows, no design annotations. Restrained, spacious, high contrast, actual TV-friendly typography. Do not invent any other sections or counters in the screenshot.
```

## 制作人员页

```text
Use case: ui-mockup
Create one polished, production-believable Android TV STAFF / voice actor detail page, as a flat full-screen UI screenshot, 1920x1080, exactly 16:9. This is a NEW page design. The provided image is only a visual style reference for Animeko's current TV subject page, not an edit target. Match its elegant large regular-weight white typography, dark translucent rounded cards, large safe margins, neutral colors and white TV focus outline. All UI copy Simplified Chinese, original name Japanese.

This is illustrative sample data using the label Sumire Uesaka, not a factual profile or verified photo. Use an editorial studio-style portrait illustration of a Japanese female voice actor with black hair and bangs on the right. Portrait is an ordinary 3:4 photo card with a natural plain background, not a cutout and not a full-screen face. The whole backdrop is dark charcoal, subtly defocused plum and warm brown sampled from that portrait. No sharp anime movie background. The design must accommodate other staff and companies later.

Use a 960x540dp grid at 2x. Left margin58dp; right58dp. Left hero content x58..600. Right portrait x688..902dp, y66..344dp with22dp rounded corners. Small muted eyebrow atx58 y42 "制作人员". Heading atx58 y68,40sp regular "上坂堇". Subheading atx58 y120,19sp muted "上坂すみれ". Metadata atx58 y159,15sp "声优 · 歌手  ·  5,286 人收藏". Do not show star ratings, birthday or unverifiable rank.
Two information cards at y205,116dp tall: left x58,width256; right x332,width256; 26dp corners.
Left is focused, white 2.5dp outline, slightly bright transparent fill, white circular arrow button in upper-right with black arrow. Heading "人物介绍"; body "活跃于动画配音与音乐领域，塑造了多种性格鲜明的角色。"; small underlined "更多".
Right is unfocused, thin grey border. Heading "讨论 · 326". Body quote "声音很有辨识度，每个角色都有自己的气质。"; small muted caption "来自用户讨论". Upper-right subdued arrow. No review scores or AI summary.
Atx58 y343 display two modest40dp-high frosted capsule buttons "浏览参与作品" with a grid icon and "查看大图" with expand icon.
Atx58 y418 heading "出演角色". A horizontal strip begins y459, containing circle-cropped anime character portraits92dp wide with20dp gaps, partly cropped at bottom as content continues downward. Show four examples, one brown-haired horse-eared anime researcher "爱丽速子", one short-haired lively anime girl "长瀞同学", one turquoise-haired anime girl "拉姆", one stylish anime idol "安娜斯塔西娅". Under each portrait place its character name then a smaller muted corresponding work caption if it fits the bottom crop. Do not show numerical character counts because records are per character AND work. The next participating-works and basic-info sections are below the fold, do not cram them into this first screen.
No browser chrome, device frame, sidebar, back button, bottom remote hints, watermarks, outside design annotations or added banners. This must look like the SAME design system as the character page: identical geometry, type hierarchy, card focus treatment and restrained buttons. No play button, follow control or personal rating control.
```


# โครงสร้างปลั๊กอิน

## ขอบเขตของแต่ละส่วน

| ส่วน | หน้าที่ |
| --- | --- |
| `*Provider.kt` | เชื่อม API ของ Cloudstream: หน้าแรก ค้นหา รายละเอียด ตอน และ callback วิดีโอ |
| `*Parser.kt` | แปลง HTML/JSON เป็นข้อมูล โดยไม่เรียกเครือข่าย เหมาะสำหรับทดสอบด้วย samples |
| `*Resolver.kt` / `*Player.kt` | อ่านเส้นทางตัวเล่นเฉพาะเว็บ; resolver ที่มีหลายขั้นตอนรับฟังก์ชัน HTTP เพื่อทดสอบโดยไม่ใช้เซิร์ฟเวอร์จริง |
| `SiteCategories.kt` / `AnimeCategories.kt` | URL/ชื่อหมวดที่เว็บแสดง และชุดหมวดหลักสำหรับหน้าแรก |
| `shared/.../CatalogPagination.kt` | สร้าง URL แบบ path/query โดยคง raw query และตรวจลิงก์ถัดไปที่ไปหน้าอื่นจริง |
| `shared/.../CdnFailoverInterceptor.kt` | ลอง CDN ที่ตัวเล่นประกาศ แล้วเก็บ URL เดิมไว้สำรอง; จำ origin ที่ตอบสำเร็จภายใน instance เดียว |
| `shared/.../BrowserSession.kt` | ใช้คุกกี้ที่มีอยู่แบบ passive และคง User-Agent เฉพาะตัวเล่น ไม่มีระบบแก้ challenge หรือ retry |
| `shared/.../PlaybackFallback.kt` | นับเฉพาะ callback วิดีโอจริง รักษา primary เมื่อ backup เสีย และส่งต่อ cancellation |
| `shared/.../ProviderBrowser.kt`, `ProviderWebView.kt` | เว็บสำรองจากหน้าตอน พร้อมควบคุมด้วยรีโมตและ cleanup ตาม lifecycle |

## ข้อกำหนดที่ต้องรักษา

- URL ในผลค้นหา/ตอนเป็น URL หน้าเว็บ; callback วิดีโอต้องเป็น media จริง ห้ามส่ง HTML/iframe เป็นไฟล์วิดีโอ
- ข้อมูลรายการใช้ cache ของแอป; URL ตัวเล่น/session/token โหลดใหม่เมื่อกดเล่น
- แต่ละ provider ที่เปิดใช้งานจำกัดคำขอหมวดพร้อมกันสามคำขอ คงหมวดหลักขนาดเล็ก
- ตัวอ่านเฉพาะเว็บเลือก container และตัวเล่น content เท่านั้น ไม่รวม sidebar/featured/ad เป็นผลค้นหาหรือวิดีโอ
- ตอนซ้ำรวมตาม season/episode (หรือ episode สำหรับเว็บที่ไม่มี season) โดยคงทุก player ที่ไม่ซ้ำ
- การสลับ CDN คง Range/Referer และ signature ของ URL เดิม ยกเลิกแล้วต้องหยุด ลิงก์ playlist ที่ไม่ต้องสลับส่งผ่านตามเดิม
- เปลี่ยนรูปแบบ CDN ผ่านตัวอ่านของเว็บนั้น ไม่ใส่กฎทุกโฮสต์ลงใน helper กลาง
- Ruka ใช้ resolver วิดีโอเดิมที่ผู้ใช้ยืนยันว่าเล่นได้
- Anime-Waku/AnimeSeven ไม่ลงทะเบียน; Movie2Free.co/FWIP ไม่อยู่ใน publication settings
  โค้ดและ samples ที่เก็บไว้ไม่ถือว่า provider เหล่านี้กลับมาใช้งานได้

## แพ็กเกจและการตรวจ

เจ็ดแพ็กเกจที่เผยแพร่คือ ExampleProvider, TwentyFiveHD, MovieDoFree, Movie2FreeHD, AHDZero, TwentyFourHD, ThaiAnime
ThaiAnime ลงทะเบียน AnimeRuka/Indy-Anime/Neko-Miku/Suba-Anime รวมทั้งหมดสิบแหล่ง
Indy ใช้ IndyAnimeProvider/IndyParser/IndyPlayer แยกจาก Site enum เดิม; อ่านเฉพาะ main catalog, กลุ่มตอน และตัวเลือกใน #mpPlayer
IndyPlayer ยอมรับ MP4 เฉพาะ host/path ที่ตรง ID ของ embed จาก Clappr ที่สังเกตได้จริง และส่งต่อ extractor/เว็บสำรองเมื่อไม่รองรับ
โค้ด shared ถูกคอมไพล์เข้าแต่ละแพ็กเกจโดย root build script; หากเปลี่ยน shared ต้องเพิ่ม version ของทุก consumer ที่สร้างใหม่

`ExampleProvider/src/test/.../com/providers/network/` ตรวจ helper ร่วม รวม response ownership, cancellation และ Android UI
แต่ละ module ตรวจ parser/resolver ของตัวเองจาก fixture ใน `samples/`; ข้อมูล fixture ต้องคง URL ต้นฉบับ ไม่สร้างเส้นทางจากชื่อไฟล์
คำสั่งสร้างและทดสอบแพ็กเกจที่เปิดใช้งานอยู่ใน README ส่วนแพ็กเกจที่ปิดทดสอบได้ผ่าน Gradle init script ที่ include สอง module ชั่วคราว
ผล unit test/HTML parser ไม่ยืนยันการเล่นจริงบน Mi Box หรือการเข้าถึงจากเครือข่ายผู้ใช้

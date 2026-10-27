# ตัวอย่างหน้าเว็บและสถานะการตรวจ

ไฟล์ที่ชื่อขึ้นต้นด้วย home/category/search/series/episode/movie และต่อท้ายด้วยชื่อเรื่องใช้ได้
ใช้ URL ใน `saved from url` หรือ canonical เป็น URL ต้นฉบับ ไม่ใช้ชื่อไฟล์เดาเส้นทางเว็บ
เก็บไฟล์ที่ผู้ใช้แนบไว้ตามเดิม พร้อมโฟลเดอร์ `_files` ที่ Chrome บันทึก iframe ลูกมาให้
การบันทึกแบบออฟไลน์เปลี่ยน `img src` บางส่วนเป็นไฟล์ในเครื่อง หากไม่มี data-src/srcset เหลืออยู่
จะไม่สามารถยืนยัน URL รูปเดิมจากไฟล์นั้นได้ ไม่สร้าง URL รูปขึ้นเอง

## ตัวอย่างใหม่ที่ตรวจแล้ว

| เว็บ | ข้อมูลที่ใช้ได้ | ข้อสังเกต |
| --- | --- | --- |
| Neko-Miku | หน้าแรก 24 รายการ หมวดและหน้าถัดไป ซีรีส์ 13 ตอน หนังและ VideoObject/embed | search เป็นคำค้นว่าง ต้องการผลค้นหาที่มีรายการเพื่อยืนยันเพิ่มเติม; โฮสต์ปลายทาง Akuma ยังไม่มี HTML |
| AnimeRuka | หน้าแรก หมวด/หน้า 2 ค้นหา รายละเอียด หนัง REST options; ซีรีส์ตัวอย่างมี 10 ตอนต่อเสียง | แยกพากย์ไทยกับซับไทย; มี HTML ตัวเล่น Abyss/AnimeMami แต่ลิงก์วิดีโอจริงยังต้องตรวจสด |
| AnimeSeven | หน้าแรก หมวด/หน้า 2 ซีรีส์/หนัง ตัวเล่นซ้อน playery → playervy → Nya | ซีรีส์ตัวอย่างมีซับไทย 4 ตอนและพากย์ไทย 1 ตอนที่มีลิงก์จริง; search เป็นคำค้นว่าง |
| Suba-Anime | หน้าแรก หมวด ค้นหา รายละเอียด หนัง ตอนและ player | search `sd` มีผลจริง 1 เรื่อง ต่อท้ายด้วยรายการใหม่ 36 เรื่องที่ต้องตัดออก; ตอนที่ 5 ของตัวอย่างยังไม่มา; เพิ่ม player-source/origin-player จากการตรวจสดและดึงชิ้นส่วนวิดีโอได้; category กับ category-page2 ทั้งสองบันทึกจากหน้า 2 |
| FWIP | รวม iframe `33202198-a103-4423-9eee-0746c6b9798c.html` และ Next.js chunks จาก fwip-player/2 | พบเงื่อนไข Playback stopped ใน `1ruy-ewmja7ms.js.ดาวน์โหลด`; API `/api/backend/videos/{id}/play`, headers x-player-origin/x-embed-origin และแหล่งเสียงเพิ่มเติม; API สดยัง 403 |
| Movie2Free.co | หน้าแรก หมวด รายละเอียด wrapper ssplayer และ FastCDN | หน้าแรก grid หลัก 36 เรื่อง ไม่รวม TOP10/แนะนำ; search บันทึกคำค้นว่าง ใช้ schema AJAX จากสคริปต์เว็บ; ตัวเล่นมีครบสามทางเลือก |
| 24HD/AHDZero/Movie2FreeHD | เก็บตัวอย่างใหม่จาก master ไว้ครบ | ตัวอย่าง regression จาก PR ก่อนอยู่ใน `samples/regression/` เพื่อคงการตรวจพฤติกรรมเดิม |

## ไฟล์ที่ช่วยตรวจการเล่นต่อได้

- FWIP: ได้ HTML ของ iframe และคลิป Chrome แล้ว ไม่ต้องส่งหน้าเดิมซ้ำ
  หากจะยืนยันรูปแบบลิงก์จริงเพิ่มเติม ขอ JSON response ของ `/api/backend/videos/{id}/play`
  และ `/thai-version/info` โดยตัด Cookie/Authorization ออก; รูปแบบ opaque ยังไม่รองรับ
- Movie2Free.co: หน้าค้นหาที่มี keyword และผลลัพธ์ หรือ JSON จาก `movie_search_ajax` เพื่อยืนยันผลค้นหาจริงเพิ่มเติม
- Neko: HTML ของหน้าปลายทาง `app.akuma-stream.com/watch/...` หลังเปิดตอนและกดเล่น
- AnimeRuka: Network request/response ของตัวเล่น AnimeMami/Abyss ที่โหลดวิดีโอ (ไม่นำลิงก์ blob ไปใช้โดยตรง)
- Suba: ถ้าตัวเล่นหลักล้มเหลว ขอผลของสำรอง1/สำรอง2 และคำขอที่โหลดวิดีโอจริง
- Neko/AnimeSeven: หน้าค้นหาที่ใส่ชื่อเรื่องแล้วมีผลลัพธ์หนึ่งหน้าต่อเว็บ

ส่ง Request URL, Method, Status และ JSON response ที่เกี่ยวข้องได้ โดยตัด Cookie/Authorization ออก
ลิงก์เล่นบางชนิดหมดอายุ จึงต้องดึงใหม่เมื่อกดเล่น ไม่เก็บ URL วิดีโอจากไฟล์ตัวอย่างเป็นค่าคงที่
ผล unit tests และ build ไม่ใช่หลักฐานว่าทุกเรื่องเล่นบน Android ได้

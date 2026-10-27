# Anime-Waku

ได้รับไฟล์ที่ `samples/Anime-Waku/` แล้ว: หน้าแรก, search, Series และ episode1/episode2 พร้อมไฟล์ตัวเล่น
ThaiAnime v7 ใช้ไฟล์ชุดนั้นเป็น regression fixtures และเพิ่ม Anime-Waku ในรายการแหล่งแล้ว

ตัวอย่างยืนยันโครงสร้าง Dooplay, dtAjax.url_api, ตัวเลือก REST, iframe `/player/` และ nya.animenani HLS bootstrap
การเล่นจริงยังต้องทดสอบบนอุปกรณ์: HTTP สดจาก sandbox ตอบ Cloudflare 403 แม้ HTML ตัวอย่างอ่านได้
ไม่ต้องส่ง cookies หรือข้อมูลบัญชี

สคริปต์ `episode1_files/front.ajax.js.ดาวน์โหลด` ระบุ URL ของ API เป็น `url_api + post + "?type=" + type + "&source=" + nume`
URL แบบ `/post/<id>/tv/1` ของ v5 ผิด; v6 แก้เป็น query parameters และเพิ่ม WebView สำรองเมื่อ HTML ต้องรัน JavaScript

v7 จับ iframe Nya ก่อน Play และอ่าน bootstrap ผ่าน HTTP; เพิ่มตัวแก้ PNG envelope ของ TS fragment
ตรวจ fragment สดด้วย production interceptor และ ffmpeg ได้ แต่ยังไม่ได้ตรวจ Waku เต็มเส้นทางบน Android

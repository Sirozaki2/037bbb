# ตัวอย่างหน้าเว็บและสถานะการตรวจ

ไฟล์ที่ชื่อขึ้นต้นด้วย home/category/search/series/episode/movie และต่อท้ายด้วยชื่อเรื่องใช้ได้
ใช้ URL ใน `saved from url` หรือ canonical เป็น URL ต้นฉบับ ไม่ใช้ชื่อไฟล์เดาเส้นทางเว็บ
เก็บไฟล์ที่ผู้ใช้แนบไว้ตามเดิม พร้อมโฟลเดอร์ `_files` ที่ Chrome บันทึก iframe ลูกมาให้
การบันทึกแบบออฟไลน์เปลี่ยน `img src` บางส่วนเป็นไฟล์ในเครื่อง หากไม่มี data-src/srcset เหลืออยู่
จะไม่สามารถยืนยัน URL รูปเดิมจากไฟล์นั้นได้ ไม่สร้าง URL รูปขึ้นเอง

## ตัวอย่างใหม่ที่ตรวจแล้ว

| เว็บ | ข้อมูลที่ใช้ได้ | ข้อสังเกต |
| --- | --- | --- |
| Neko-Miku | หน้าแรก 24 รายการ หมวดและหน้าถัดไป ซีรีส์ 13 ตอน หนังและ VideoObject/embed | search เป็นคำค้นว่าง ต้องการผลค้นหาที่มีรายการเพื่อยืนยันเพิ่มเติม; เพิ่มตัวอย่าง Akuma ใน regression แล้ว; ตรวจสดหน้า/playlist และชิ้นวิดีโอผ่าน แต่ยังไม่ยืนยันการเล่นต่อเนื่องบน Android |
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
- Neko: หากยังเล่นไม่ได้ ขอ URL เรื่อง/ตอนและเวอร์ชัน Cloudstream เพื่อเทียบกับ Akuma ตัวอย่างที่ตรวจผ่านแล้ว
- AnimeRuka: Network request/response ของตัวเล่น AnimeMami/Abyss ที่โหลดวิดีโอ (ไม่นำลิงก์ blob ไปใช้โดยตรง)
- Suba: ถ้าตัวเล่นหลักล้มเหลว ขอผลของสำรอง1/สำรอง2 และคำขอที่โหลดวิดีโอจริง
- Neko/AnimeSeven: หน้าค้นหาที่ใส่ชื่อเรื่องแล้วมีผลลัพธ์หนึ่งหน้าต่อเว็บ

ส่ง Request URL, Method, Status และ JSON response ที่เกี่ยวข้องได้ โดยตัด Cookie/Authorization ออก
ลิงก์เล่นบางชนิดหมดอายุ จึงต้องดึงใหม่เมื่อกดเล่น ไม่เก็บ URL วิดีโอจากไฟล์ตัวอย่างเป็นค่าคงที่
ผล unit tests และ build ไม่ใช่หลักฐานว่าทุกเรื่องเล่นบน Android ได้

## แก้ตามรายงาน Android หลัง merge

- **24HD v3**: escape วงเล็บปีกกาปิดใน regex ของ CSS และ PLAYER_CONFIG ให้ ICU บน Android ยอมรับ; รูปแบบเดิมผ่าน JVM แต่ล้มบน Android
- **ThaiAnime v2**: แก้ regex ชนิดเดียวกันใน AnimeRuka/Suba; ส่ง Referer ของ wrapper และเพิ่มการอ่าน Akuma ของ Neko โดยดึง token ใหม่เมื่อกดเล่น ใช้ URL หน้าตัวเล่นเต็มเป็น Referer ของ HLS
- **Moviedofree v4 / AHDZero v3**: โหลดหมวดพร้อมกันได้สูงสุดสามคำขอ แทนรอทีละหมวด (57/42 หมวด) คงรายการเมนูเดิม ความเร็วยังขึ้นกับเว็บและ Cloudflare
- **Movie2Free.co v2**: รองรับชิ้น MPEG-TS ของ FastCDN ที่มี PNG 545 ไบต์นำหน้า ตรวจ signature และ TS sync ก่อนตัด ไม่แก้ playlist หรือไฟล์ที่ไม่ตรงรูปแบบ รองรับการ seek ด้วยการส่ง body เต็มและให้ Media3 ข้ามตำแหน่งตาม Range เดิม
- **Movie2FreeHD v2**: แสดงเหตุผลเมื่อไม่มีตัวเล่น เรื่อง `dont-say-good-luck` ที่ตรวจไม่มี options/iframe จึงยังสร้างลิงก์เล่นให้ไม่ได้ ตัวอย่าง regression เก็บกรณีนี้ไว้

ICU ตรวจ regex จริงทั้ง 27 รูปแบบในสาม parser: ก่อนแก้ผิด 4 จุด หลังแก้ผ่านทั้งหมด
การตรวจ Akuma ได้หน้า/master/variant HTTP200 และชิ้นวิดีโอ HTTP206 เป็นหลักฐานการดึงข้อมูล ไม่ใช่ผลเล่นบน Android

**ยังไม่ยืนยันการแก้:** AnimeSeven HTTP403, FWIP API403/อาการ source error 2004, และการเล่น Ruka ผ่าน Abyss/AnimeMami
การแก้ regex ของ Ruka ไม่ได้แปลว่าโฮสต์วิดีโอทั้งหมดรองรับแล้ว Movie2Free.co ยังต้องลองเรื่องที่ผู้ใช้พบ No Links Found โดยตรง
ต้องการ URL เรื่อง/ตอนที่เสียและเวอร์ชัน Cloudstream สำหรับตรวจต่อ ไม่มีการข้าม Cloudflare รับประกันได้

## ผลทดสอบจากผู้ใช้และแพตช์ถัดจาก PR #3

ผู้ใช้ Cloudstream 4.8.0 ยืนยันว่า 24HD, 25HD, 037-HDD, Moviedofree, AHDZero, Movie2FreeHD และ Neko-Miku เล่นได้แล้ว
ยังรายงาน AnimeSeven HTTP403 และ AnimeRuka/Movie2Free.co/FWIP No Links Found

- **Movie2FreeHD v3** เปลี่ยนการโหลด 41 หมวดจากทีละคำขอเป็นพร้อมกันสูงสุดสามคำขอ คงหมวด/ชื่อ/การแบ่งหน้าเดิม
  Moviedofree/AHDZero มีการจำกัดพร้อมกันสามคำขอตั้งแต่ PR #3 แต่ยังต้องรอทั้ง57/42หมวดก่อน Cloudstream แสดงหน้าแรก
  ไม่รับรองเวลาที่ลดลงบนอุปกรณ์จริง; การลดจำนวนหมวดต้องเลือกเปลี่ยนเมนูแยกต่างหาก
- **ThaiAnime v3** เพิ่ม AnimeMami สำหรับ Ruka โดยอ่าน `#app[data-page]` component `Player/Show` เฉพาะ `props.video.url`
  และคำบรรยายจาก `subtitle_path` ไม่อ่าน default/error/ad video รูปแบบ `.txt` เป็น HLS ตามสคริปต์ตัวเล่น
  ตัวดัก HTTP แกะเฉพาะ JSON `{p: base64}` ที่ถอดแล้วขึ้นต้น `#EXTM3U` บนโฮสต์ CDN ของลิงก์นั้น
  จำกัดการตรวจไม่เกิน1MiBและไม่แก้ข้อมูล MP4/segment/playlistปกติ รุ่นปัจจุบันใช้คุกกี้เดิมโดยไม่เปิดระบบยืนยันอัตโนมัติ
  ดึงหน้า AnimeMami ใหม่ทุกครั้งที่กดเล่น ไม่ฝัง URL วิดีโอจากตัวอย่าง
- หลักฐานเพิ่มเติม: `https://animemami.xyz/build/assets/Show-CjrBPCtA.js` ตอบ200; โค้ดตัวเล่นจริงใช้ `.txt` เป็นHLSและแกะJSON.pด้วยatob
  CDN ของตัวอย่างตอบ403ใน sandbox จึงยัง **ไม่ยืนยันว่าตอนที่ใช้ AnimeMami เล่นได้บน Android**
- Abyss ของ Ruka ยังต้องมีตัวส่งข้อมูล chunk/service-worker โดยเฉพาะ ข้อมูล bootstrap เป็น AES-CTR และมี virtual MP4 URLs
  ไม่ควรส่ง URL เสมือนเหล่านั้นให้ native player ตรง ๆ ยังไม่มีแพตช์การส่งข้อมูล Abyss ในรอบนี้
- Movie2Free.co/FWIP/AnimeSeven ยังไม่มีหลักฐานการแก้สำเร็จ ไม่ต้องเปิด F12 เพื่อเก็บ HAR เพราะ FWIP มีเงื่อนไขสั่งหยุดเมื่อพบ DevTools

## ตรวจซ้ำตามภาพรายงาน 28 ตุลาคม 2026

ผู้ใช้ยืนยันว่า **AnimeRuka เล่นได้แล้ว** จึงคงตัวอ่าน AnimeMami/Abyss เดิมไว้
ภาพรายงานเดิมระบุ FWIP TV/Movie2Free.co No Links Found และ AnimeSeven HTTP403

- Movie2Free.co: หน้าหนัง `drawn-together-2026` ถูกปฏิเสธ403จาก sandbox แต่ wrapper `backup.ssplayer168.xyz` และ FastCDN ของตัวอย่างยังตอบ200
  master HLS มีเสียงไทยและภาพ360p/720p เพิ่มทางค้นหา content iframe ผ่าน native WebView สูงสุด8วินาทีใน diagnostic4
  แล้วใช้ resolver/FastCDN interceptor เดิม ทดสอบกรณี HTTPหน้าหนังถูกปฏิเสธแต่พบ wrapper ผ่าน browser
  ยังไม่เปิดเผยแพร่และยังไม่ยืนยันการเล่นบน Android
- FWIP TV: ตรวจ subdomain จริง `nom-na.player123up.xyz` พร้อม API path/headers ตาม JavaScript แล้ว
  `/api/backend/videos/<id>/play` และ `/player/settings` ยังตอบ403 ทั้ง HTTP และหน้าตัวเล่นใน Chromium ถูกบล็อก
  สคริปต์มี delivery `protected-opaque` และ guard ที่สั่ง Playback stopped; ยังไม่มี JSON สดให้ยืนยันรูปแบบการส่งวิดีโอ
  diagnostic5 เก็บ HTTP error ไว้ในข้อความเพื่อแยกกรณี APIถูกปฏิเสธจากรูปแบบสตรีมที่ไม่รองรับ
  ไม่เดาว่า opaque URL เป็นMP4/HLS และไม่เปิดเผยแพร่
- AnimeSeven: หน้าตอนบนwwwและหน้าแรกแบบไม่มีwwwยังตอบ403; Chromiumแสดง blocked page
  คงสถานะเปิดเว็บเท่านั้น ต้องเข้าถึงหน้าจริงได้ก่อนจึงจะยืนยันตัวเล่นสดและเปิด provider ได้

แพ็กเกจปัจจุบันถอดการยืนยัน Cloudflare อัตโนมัติตามคำขอแล้ว คงคุกกี้เดิมและการเปิดเว็บสำรอง
การถอดตัวช่วยไม่เปลี่ยน HTTP403 ของเซิร์ฟเวอร์ให้เป็น200 และผลจากเครือข่าย sandbox ไม่ใช่ผลเครือข่ายผู้ใช้

## รอบปรับหมวดหมู่และปิด Waku (ThaiAnime v11)

- ปิด Anime-Waku ตามรายงาน No links found และเอาเมนูเว็บสำรองออกจากหน้าตั้งค่าทุกแพ็กเกจ
  AnimeSeven/Movie2Free.co/FWIP ยังคงปิด; ยังมีเว็บสำรองจากหน้าตอนเมื่ออ่านวิดีโอไม่สำเร็จ
- Moviedofree: ตรวจสดหมวดตัวอย่างหนังตอบ200แต่ไม่มีข้อมูล จึงแทนด้วย Action ในหน้าแรก
  หนังไทย/การ์ตูน/Action/Netflix/หมวดปีและหน้าปีที่สองมี32รายการ; เมนูที่จับใหม่อยู่ใน `regression/moviedofree.com/menu.html`
- ตรวจสดทุกหมวดหลักของ037-HDD/25-HD/Movie2FreeHD ตอบ200และตัวอ่านได้รายการจริง
  AHDZero/24HD/Neko/Ruka/Suba ตอบ403จากเครือข่ายทดสอบรอบนี้ จึงตรวจโครงสร้างจาก samples
- ผลตรวจหมวด/ตัวอ่านไม่ยืนยันการเล่นทุกเรื่องบน Mi Box; ตัวอ่านวิดีโอ Ruka คงเดิมตามที่ผู้ใช้ยืนยันว่าเล่นได้

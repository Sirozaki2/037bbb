## สถานะล่าสุด

- ปิด AnimeSeven, Movie2Free.co และ FWIP TV ตามคำขอ: ไม่เผยแพร่สองแพ็กเกจหลัง และ ThaiAnime ไม่ลงทะเบียน AnimeSeven
  เก็บ source และ samples เดิมไว้อ้างอิงเท่านั้น หากเคยติดตั้ง Movie2Free/FWIPTV ให้ถอนส่วนขยายทั้งสองในแอปด้วย
  อัปเดต ThaiAnime v6 แล้วเริ่มแอปใหม่เพื่อเอา AnimeSeven ออกจากรายการ
- หน้าแรกเหลือหมวดหลัก: 037-HDD 5, 25-HD 5, Moviedofree 4, AHDZero 4, 24HD 5, Movie2FreeHD 4,
  Neko-Miku 4 และ AnimeRuka 5; Suba-Anime คงเมนูเดิม
  หมวดรองปี/ประเทศ/genre ไม่แสดงในตัวกรองหน้าแรก แต่ยังค้นหาชื่อเรื่องและแบ่งหน้าหมวดหลักได้
  Cloudstream 4.8.0 รอทุกหมวดก่อนคืนผล การลดคำขอจึงลดงานเริ่มต้น ไม่ใช่การรับประกันเวลาเครือข่าย
- ThaiAnime v5 เพิ่ม Anime-Waku จาก `samples/Anime-Waku/`: หน้าแรก 5 หมวดหลัก ค้นหา รายละเอียดและตอน
  อ่าน REST `url_api` และตัวเล่น `/player/` ไปยัง nya.animenani แล้วอ่าน HLS bootstrap ใหม่ตอนกดเล่น
  ใช้ iframe ที่เว็บแสดงก่อน และเรียก REST สำรองเมื่อยังไม่พบลิงก์
  v6 แก้ URL ของ REST เป็น `/post/<id>?type=tv&source=<หมายเลข>` ตาม JavaScript จริงของเว็บ
  รองรับ API ที่ส่ง iframe HTML และทดสอบกรณี HTML เริ่มต้นยังไม่มี iframe
  หาก HTTP ยังไม่พบลิงก์ จะเปิดหน้าตอนใน WebView ของแอปด้วย native networking สูงสุด 15 วินาที
  จับเฉพาะ HLS route ที่พบในตัวเล่นจริง พร้อม Referer/User-Agent ของคำขอนั้น
  ตัวอ่านทดสอบกับ HTML สองตอนแล้ว; HTTP สดจาก sandbox ยังถูก Cloudflare 403 และยังไม่ยืนยัน WebView/การเล่นบน Android
- ตอนนี้เผยแพร่ 7 แพ็กเกจ รวม 10 แหล่ง (รวม Anime-Waku)

## ปลั๊กอินสำหรับ Cloudstream

เพิ่ม repository ด้วย `https://raw.githubusercontent.com/jasdwa22/037bb/master/repo.json`
แล้วติดตั้งปลั๊กอินที่ต้องการจากรายการส่วนขยาย:

| ปลั๊กอิน | ชื่อแหล่งในแอป | รองรับ |
| --- | --- | --- |
| ExampleProvider | 037-HDD | หนังและซีรีส์ |
| TwentyFiveHD | 25-HD | หนัง ค้นหา ซีซันและตอนที่เว็บมีลิงก์แล้ว |
| MovieDoFree | Moviedofree | หนังและค้นหา (ซ่อนเมนูซีรีส์ที่ยังไม่รองรับ) |
| AHDZero | AHDZero | อนิเมะ ซีรีส์และเดอะมูฟวี่ พากย์ไทย/ซับไทย |
| TwentyFourHD | 24HD | หนังและซีรีส์ แยกตอนตามลิงก์ที่มีจริง |
| Movie2FreeHD | Movie2FreeHD | หนัง/ซีรีส์ ค้นหา หมวดหมู่ ตอนและแหล่งเสียงไทย/ซับไทย |
| ThaiAnime | Neko-Miku, AnimeRuka, Suba-Anime, Anime-Waku | ทดลองสำหรับ beta: อนิเมะ/หนัง ค้นหา หมวดหมู่ แยกเสียงและตอน; การเล่นยังต้องทดสอบบนอุปกรณ์ |

ปลั๊กอินเดิมติดตั้งแยกกันได้ ส่วน ThaiAnime ติดตั้งครั้งเดียวจะเพิ่มแหล่งอนิเมะทั้งสี่เว็บ สำหรับ 25-HD วิดีโอบางรายการรวมหลายเสียงและคำบรรยายไว้ในลิงก์เดียว
ให้เลือกแทร็กเสียงและคำบรรยายในเครื่องเล่น ความพร้อมของวิดีโอขึ้นอยู่กับเว็บต้นทาง

เวอร์ชัน 037-HDD v7, 25-HD v2 และ Moviedofree v2 เพิ่มหมวดหมู่ตามเมนูของแต่ละเว็บ
รวมประเภท ประเทศ ผู้ให้บริการ และปีที่เว็บแสดง เลือกหมวดที่ต้องการในตัวกรองหน้าแรกได้
บางหมวดอาจว่างตามเว็บต้นทาง

25-HD v2 อ่านเส้นทาง CDN จาก HLS content steering เพื่อโหลดชิ้นส่วนวิดีโอ เสียง และซับ
ยังคงใช้ master playlist เดิมเพื่อให้เลือกแทร็กเสียงและคำบรรยายในเครื่องเล่นได้
Moviedofree v2 เพิ่ม Referer สำหรับรูป รองรับรูป lazy-load และแก้การเปลี่ยนหน้าของหมวดปี

AHDZero v1 และ 24HD v1 อ่านหมวดหมู่และตอนจาก HTML ตัวอย่างใน `samples/`
AHDZero มีหมวดซับไทย พากย์ไทย เดอะมูฟวี่ และ genre; 24HD มีประเภทและชุดหนังตาม tag
24HD อ่านรูปปกจาก CSS รวมรูปที่เว็บซ่อนด้วย lazy-load และใช้ลิงก์เปลี่ยนหน้าของรายการหลัก
ทั้งสองใช้ CloudflareKiller ของ Cloudstream เมื่อเว็บส่ง challenge แต่ยังต้องทดสอบบนเครื่องจริง
การตรวจใน sandbox ยืนยันตัวอ่าน HTML และไฟล์สตรีมตัวอย่าง ไม่ยืนยันว่า Cloudflare ผ่านทุกเครื่อง

## ประวัติการเพิ่มและแก้ไข (บางแหล่งปิดแล้วตามสถานะล่าสุด)

- Movie2FreeHD v1 ใช้หน้า `/movies/` และ `/series/` สำหรับแบ่งหน้า อ่านผลค้นหาแยกจากรายการแนะนำ
  รูปปกรองรับ CSS และ lazy-load; ตอนเรียงตามซีซัน/เลขตอน และรวมลิงก์ซ้ำโดยคงชื่อพากย์ไทย/ซับไทยไว้
  ดึงลิงก์ Meeplayer ใหม่เมื่อกดเล่น รักษา HLS master สำหรับแทร็กเสียง และลอง CDN สำรองเมื่อโหลดชิ้นส่วนวิดีโอผิดพลาด
- Movie2Free.co v1 เพิ่มเป็นปลั๊กอินแยกจาก Movie2FreeHD อ่านเฉพาะรายการหลัก 36 เรื่องในหน้าแรก
  ไม่ปนแถว TOP 10/แนะนำ มีหมวดหนังและค้นหาผ่าน AJAX ของเว็บ พร้อมรายละเอียด/ปี/ประเภท
  อ่าน wrapper ssplayer และตัวเล่น FastCDN/ตัวเล่นไว/สำรองใหม่ทุกครั้ง รักษา Referer
  ข้าม mirror ที่เสียโดยยังลองตัวที่เหลือ จำกัดการวน iframe และไม่ดึง URL โฆษณามาเป็นหนัง
  ใช้ `status = 3` จนกว่าจะทดสอบ Cloudflare และการเล่นจริงบน Android
- FWIP TV v3 อ่าน API `player123up.xyz` ตามสคริปต์ตัวเล่นที่ผู้ใช้ส่งมา
  ส่ง origin ของ iframe/เว็บแม่ ดึงลิงก์ใหม่ แยกแหล่งเสียงที่พร้อม และลอง backup ผ่าน extractor ของแอป
  รับเฉพาะ HLS/ไฟล์วิดีโอที่ระบุรูปแบบได้ ไม่แสดง iframe หรือ URL opaque ที่ยังไม่ยืนยันเป็นวิดีโอ
  คง `status = 3`: API จริงยังตอบ 403 จาก sandbox จึงยังไม่ยืนยันการเล่นบน Android
  HTML ใหม่ยืนยันว่า “Playback stopped.” ถูกแสดงเมื่อ guard ตรวจ DevTools ทำงาน
  รวมคลิกขวา, F12, คีย์ลัด Inspect/View source และการตรวจจับอัตโนมัติเมื่อเปิด blockDevTools
  เป็นเงื่อนไขบนเว็บต้นทาง การแก้ provider ไม่ได้เปลี่ยน JavaScript ของเว็บไซต์
- ThaiAnime v1 เพิ่ม Neko-Miku, AnimeRuka, AnimeSeven และ Suba-Anime จาก HTML ที่ผู้ใช้บันทึก
  เมนูมาจากเว็บจริง แยก Anime/AnimeMovie, พากย์ไทย/ซับไทยและซีซัน โดยไม่แสดงตอนที่ยังไม่มีลิงก์
  ค้นหา Suba อ่านเฉพาะผลค้นหา ไม่รวมรายการล่าสุดที่เว็บแทรกไว้; รองรับรูป lazy-load และการแบ่งหน้าของแต่ละเว็บ
  AnimeRuka อ่านตัวเลือก REST ใหม่ทุกครั้งที่เล่น; AnimeSeven อ่านตัวเล่นซ้อนและ manifest จาก bootstrap ใหม่
  Suba อ่าน content iframe ของ Kokora ไปยัง anim-esun และเก็บลิงก์วิดีโอจริงตามความละเอียด โดยไม่ใช้ preroll
  โฮสต์ Akuma/Abyss/AnimeMami/OK.ru ส่งต่อให้ extractor ที่มีในแอป ความพร้อมขึ้นกับแอปและเว็บปลายทาง
  ตั้ง status = 3 จนกว่าจะตรวจการเล่นบนอุปกรณ์จริงได้
- 24HD v2 และ AHDZero v2 ใช้ HTTP cache ของแอปสำหรับรายการ/รายละเอียด และโหลดหน้าใหม่ตอนกดเล่น
  ลดการโหลดหน้าแรกซ้ำทางเครือข่ายของ 24HD; รองรับปุ่ม load-more ที่มี `data-next-page` แบบ relative
  AHDZero เรียงตอนตามเลข และ 24HD อ่านรูป lazy-load เมื่อไม่มี CSS รูปปก
- 25-HD v3 แก้ชื่อเมนูปีให้ตรง URL, รองรับรูป lazy-load และไม่ทิ้ง master playlist เมื่อโหลด steering ไม่สำเร็จ
  ตัวเล่นสำรองที่ล้มเหลวไม่ทำให้ลิงก์ที่เจอแล้วหายไป
- 037-HDD v8 เก็บ query เมื่อเปลี่ยนหน้า และข้าม extractor สำรองที่ผิดพลาดเพื่อทำงานกับแหล่งถัดไป
- Moviedofree v3 ซ่อนเมนูซีรีส์ที่ไม่ตรงกับความสามารถของ provider (ยังรองรับหนังเท่านั้น)
  ตรวจหน้าหมวดซีรีส์ฝรั่งแล้วเว็บส่ง “ไม่พบข้อมูล”

### สถานะการตรวจเว็บ

Movie2FreeHD ตรวจหน้าแรก/หนัง/ซีรีส์/ค้นหา/หน้าที่สองและ API ตัวเล่นจริงได้
ตรวจ Meeplayer ของซีรีส์ตัวอย่างถึง HLS master, media playlist และข้อมูลชิ้นส่วนวิดีโอแบบจำกัดขนาดแล้ว
ไม่ได้ยืนยันว่าทุกเรื่องหรือทุกแทร็กเสียงเล่นได้ และยังไม่ได้ทดสอบแอปบนอุปกรณ์ Android
037-HDD, 25-HD และ Moviedofree ตรวจตัวอ่านกับ HTML หน้าแรกที่ดึงใหม่ได้
24HD, AHDZero และ FWIP ถูก Cloudflare บล็อกใน sandbox จึงใช้ HTML ตัวอย่างและ unit tests ตรวจโครงสร้าง

Nekomiku (`nekomiku.net`), AnimeRuka (`animeruka.com`), AnimeSeven (`www.anime-seven.com`)
และ Suba-Anime (`suba-anime.com`) เพิ่มในแพ็กเกจ ThaiAnime แล้ว โดยใช้ samples ที่ผู้ใช้เพิ่มบน master
สามเว็บแรกและ FWIP/ตัวเล่น player123up ยังถูก Cloudflare บล็อกทั้ง HTTP และ Chromium
Suba เปิดหน้าเว็บและตัวเล่นใน Chromium ได้ ตรวจ content source ที่ anim-esun และดึงชิ้นส่วนวิดีโอ
1,024 ไบต์ผ่าน Chromium ได้ HTTP 206 (video/x-m4v) แต่ curl ดึงไฟล์เดียวกันหมดเวลา
จึงยังไม่ได้ยืนยันเส้นทางโหลดวิดีโอของแอปหรือการเล่นจนจบบน Android
ผล tests ยืนยันตัวอ่าน HTML/ตัวเลือกเสียง/เส้นทางตัวเล่น ไม่ยืนยันว่าโฮสต์ทุกตัวเล่นได้
รายละเอียดไฟล์ที่ใช้ได้และตัวอย่างที่ยังขาดอยู่ใน [samples/README.md](samples/README.md)
Movie2FreeHD และ Movie2Free.co เป็นคนละเว็บและมีปลั๊กอินแยกกัน
Movie2Free.co ตรวจสด wrapper/FastCDN/HLS master/variant และ media range ได้ แต่หน้าเว็บหลักติด Cloudflare
FastCDN ตัวอย่างมี PNG prefix 545 ไบต์ก่อน MPEG-TS จึงยังต้องทดสอบความเข้ากันได้กับตัวเล่น Android

ทดสอบทุก provider และสร้างแพ็กเกจ:

```bash
GITHUB_REPOSITORY=jasdwa22/037bb ./gradlew make makePluginsJson testDebugUnitTest
```

ใช้ JDK 17, Android SDK 35 และกำหนด `ANDROID_HOME` ให้ถูกต้อง
Unit tests ใช้ HTML fixtures และ API จำลองในหน่วยทดสอบ จึงไม่ต้องเข้าถึงเว็บต้นทางและไม่อ่านไฟล์ `temp`

---

**⚠️ This is currently under development, dont use it yet if you're not comfortable with constantly merging new changes**

# `Cloudstream3 Plugin Repo Template`

Template for a [Cloudstream3](https://github.com/recloudstream) plugin repo

**⚠️ Make sure you check "Include all branches" when using this template**

 
## Getting started with writing your first plugin

This template includes 1 example plugin.

1. Open the root build.gradle.kts, read the comments and replace all the placeholders
2. Familiarize yourself with the project structure. Most files are commented
3. Build or deploy your first plugin using:
   - Windows: `.\gradlew.bat ExampleProvider:make` or `.\gradlew.bat ExampleProvider:deployWithAdb`
   - Linux & Mac: `./gradlew ExampleProvider:make` or `./gradlew ExampleProvider:deployWithAdb`


## Granting All Files Access on Newer Android Devices

For local plugin testing, you need to grant the app "All Files Access" on newer Android devices (Android 11 and above). Here’s how to do it:

### Using ADB

* `adb shell appops set --uid PACKAGE_NAME MANAGE_EXTERNAL_STORAGE allow`
* Replace `PACKAGE_NAME` with the name of the package for the Cloudstream3 version you are using:
   - debug: `com.lagradost.cloudstream3.prerelease.debug`
   - prerelease: `com.lagradost.cloudstream3.prerelease`
   - stable: `com.lagradost.cloudstream3`

### Manually

1. **Open Settings**: Go to your device’s Settings menu.

2. **Navigate to Special Access**:
   - Tap on "Apps & notifications" or "Apps".
   - Select "Special app access" or "Special access".

3. **Select All Files Access**:
   - Tap on "All files access".
   - It may be under the three vertical dots menu towards the top of the screen.

4. **Grant Access to the App**: Find the app in the list and tap on it to toggle it, if it is not already enabled.

6. **Restart the App**: Close and reopen the app to apply the changes.


## License

Everything in this repo is released into the public domain. You may use it however you want with no conditions whatsoever


## Attribution

This template as well as the gradle plugin and the whole plugin system is **heavily** based on [Aliucord](https://github.com/Aliucord).
*Go use it, it's a great mobile discord client mod!*

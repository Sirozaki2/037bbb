package com.ahdzero

// Navigation captured from the supplied home.html; retain the website taxonomy.
internal object SiteCategories {
    val entries = listOf(
        "" to "ล่าสุด",
        "/category/1" to "ซับไทย",
        "/category/2" to "พากย์ไทย",
        "/category/3" to "เดอะมูฟวี่",
        "/genre/action" to "แอคชั่น",
        "/genre/adventure" to "ผจญภัย",
        "/genre/avant-garde" to "อาวองการ์ด",
        "/genre/award-winning" to "ได้รับรางวัล",
        "/genre/boys-love" to "บอยเลิฟ",
        "/genre/comedy" to "ตลก",
        "/genre/drama" to "ดราม่า",
        "/genre/ecchi" to "เอ็คจิ",
        "/genre/fantasy" to "แฟนตาซี",
        "/genre/girls-love" to "เกิร์ลเลิฟ",
        "/genre/gourmet" to "อาหาร",
        "/genre/harem" to "ฮาเร็ม",
        "/genre/horror" to "สยองขวัญ",
        "/genre/isekai" to "อิเซไค",
        "/genre/josei" to "โจเซ",
        "/genre/kids" to "เด็ก",
        "/genre/martial-arts" to "ศิลปะการต่อสู้",
        "/genre/mecha" to "หุ่นยนต์",
        "/genre/military" to "ทหาร",
        "/genre/music" to "ดนตรี",
        "/genre/mystery" to "ลึกลับ",
        "/genre/parody" to "ล้อเลียน",
        "/genre/psychological" to "จิตวิทยา",
        "/genre/reverse-harem" to "รีเวิร์สฮาเร็ม",
        "/genre/romance" to "โรแมนซ์",
        "/genre/school" to "โรงเรียน",
        "/genre/sci-fi" to "ไซไฟ",
        "/genre/seinen" to "เซเน็น",
        "/genre/shoujo" to "โชโจ",
        "/genre/shounen" to "โชเน็น",
        "/genre/slice-of-life" to "ชีวิตประจำวัน",
        "/genre/space" to "อวกาศ",
        "/genre/sports" to "กีฬา",
        "/genre/super-power" to "พลังวิเศษ",
        "/genre/supernatural" to "เหนือธรรมชาติ",
        "/genre/suspense" to "ซัสเพนส์",
        "/genre/thriller" to "ทริลเลอร์",
        "/genre/time-travel" to "ย้อนเวลา",
    )

    // Cloudstream waits for every home row; keep the initial request set small.
    val homeEntries = listOf(
        "" to "ล่าสุด",
        "/category/1" to "ซับไทย",
        "/category/2" to "พากย์ไทย",
        "/category/3" to "เดอะมูฟวี่",
    )
}

package com.thaianime

internal object AnimeCategories {
    val entries = mapOf(
        Site.WAKU to listOf(
            "/" to "อัปเดตล่าสุด",
            "/anime/" to "อนิเมะทั้งหมด",
            "/catalog/%e0%b8%8b%e0%b8%b1%e0%b8%9a%e0%b9%84%e0%b8%97%e0%b8%a2/" to "ซับไทย",
            "/catalog/%e0%b8%9e%e0%b8%b2%e0%b8%81%e0%b8%a2%e0%b9%8c%e0%b9%84%e0%b8%97%e0%b8%a2/" to "พากย์ไทย",
            "/category/%e0%b9%80%e0%b8%94%e0%b8%ad%e0%b8%b0%e0%b8%a1%e0%b8%b9%e0%b8%9f%e0%b8%a7%e0%b8%b5%e0%b9%88/" to "เดอะมูฟวี่",
        ),
        Site.NEKO to listOf(
            "/" to "อัปเดตล่าสุด",
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
        ),
        Site.RUKA to listOf(
            "/" to "อัปเดตล่าสุด",
            "/anime/" to "อนิเมะทั้งหมด",
            "/catalog/%e0%b8%8b%e0%b8%b1%e0%b8%9a%e0%b9%84%e0%b8%97%e0%b8%a2/" to "ซับไทย",
            "/catalog/%e0%b8%9e%e0%b8%b2%e0%b8%81%e0%b8%a2%e0%b9%8c%e0%b9%84%e0%b8%97%e0%b8%a2/" to "พากย์ไทย",
            "/category/%e0%b9%80%e0%b8%94%e0%b8%ad%e0%b8%b0%e0%b8%a1%e0%b8%b9%e0%b8%9f%e0%b8%a7%e0%b8%b5%e0%b9%88/" to "เดอะมูฟวี่",
            "/genre/action/" to "Action (ต่อสู้)",
            "/genre/action-adventure/" to "Action & Adventure",
            "/genre/adventure/" to "Adventure (ผจญภัย)",
            "/genre/animation/" to "Animation",
            "/genre/cars/" to "Cars (รถ)",
            "/genre/comedy/" to "Comedy (ตลก)",
            "/genre/crime/" to "Crime",
            "/genre/demons/" to "Demons (ปีศาจ)",
        ),
        Site.SEVEN to listOf(
            "/catalog/0/%E0%B8%AD%E0%B8%99%E0%B8%B4%E0%B9%80%E0%B8%A1%E0%B8%B0%E0%B8%AD%E0%B8%B1%E0%B8%9E%E0%B9%80%E0%B8%94%E0%B8%95%E0%B8%A5%E0%B9%88%E0%B8%B2%E0%B8%AA%E0%B8%B8%E0%B8%94.html" to "รายชื่ออนิเมะทั้งหมด >>",
            "/catalog/0/%E0%B8%AD%E0%B8%99%E0%B8%B4%E0%B9%80%E0%B8%A1%E0%B8%B0%E0%B8%97%E0%B8%B1%E0%B9%89%E0%B8%87%E0%B8%AB%E0%B8%A1%E0%B8%94.html" to "อนิเมะทั้งหมด",
            "/catalog/15/%E0%B8%8B%E0%B8%B1%E0%B8%9A%E0%B9%84%E0%B8%97%E0%B8%A2.html" to "ซับไทย",
            "/catalog/16/%E0%B8%9E%E0%B8%B2%E0%B8%81%E0%B8%A2%E0%B9%8C%E0%B9%84%E0%B8%97%E0%B8%A2.html" to "พากย์ไทย",
            "/catalog/17/Movie.html" to "Movie",
            "/catalog/20/%E0%B8%AD%E0%B8%99%E0%B8%B4%E0%B9%80%E0%B8%A1%E0%B8%B0%20Hentai.html" to "อนิเมะ Hentai",
            "/catalog/21/%E0%B8%88%E0%B8%9A%E0%B9%81%E0%B8%A5%E0%B9%89%E0%B8%A7.html" to "จบแล้ว",
            "/catalog/22/%E0%B8%A2%E0%B8%B1%E0%B8%87%E0%B9%84%E0%B8%A1%E0%B9%88%E0%B8%88%E0%B8%9A.html" to "ยังไม่จบ",
            "/catalog/23/%E0%B8%81%E0%B8%B3%E0%B8%A5%E0%B8%B1%E0%B8%87%E0%B8%A1%E0%B8%B2-(%E0%B8%81%E0%B8%B2%E0%B8%A3%E0%B9%8C%E0%B8%95%E0%B8%B9%E0%B8%99%E0%B9%83%E0%B8%AB%E0%B8%A1%E0%B9%88).html" to "กำลังมา-(การ์ตูนใหม่)",
            "/catalog/24/%E0%B8%AD%E0%B8%99%E0%B8%B4%E0%B9%80%E0%B8%A1%E0%B8%B0%2018+.html" to "อนิเมะ 18+",
            "/catalog/25/%E0%B8%AB%E0%B8%99%E0%B8%B1%E0%B8%87-%E0%B8%8B%E0%B8%B5%E0%B8%A3%E0%B8%B5%E0%B8%A2%E0%B9%8C.html" to "หนัง-ซีรีย์",
        ),
        Site.SUBA to listOf(
            "/" to "อัปเดตล่าสุด",
            "/%E0%B8%AD%E0%B8%99%E0%B8%B4%E0%B9%80%E0%B8%A1%E0%B8%B0%E0%B8%9E%E0%B8%B2%E0%B8%81%E0%B8%A2%E0%B9%8C%E0%B9%84%E0%B8%97%E0%B8%A2" to "อนิเมะพากย์ไทย",
            "/%E0%B8%AD%E0%B8%99%E0%B8%B4%E0%B9%80%E0%B8%A1%E0%B8%B0%E0%B8%8B%E0%B8%B1%E0%B8%9A%E0%B9%84%E0%B8%97%E0%B8%A2" to "อนิเมะซับไทย",
            "/%E0%B8%AD%E0%B8%99%E0%B8%B4%E0%B9%80%E0%B8%A1%E0%B8%B0%E0%B8%88%E0%B8%9A%E0%B9%81%E0%B8%A5%E0%B9%89%E0%B8%A7" to "อนิเมะจบแล้ว",
            "/%E0%B8%AD%E0%B8%99%E0%B8%B4%E0%B9%80%E0%B8%A1%E0%B8%B0%E0%B8%A2%E0%B8%B1%E0%B8%87%E0%B9%84%E0%B8%A1%E0%B9%88%E0%B8%88%E0%B8%9A" to "อนิเมะยังไม่จบ",
            "/%E0%B8%AD%E0%B8%99%E0%B8%B4%E0%B9%80%E0%B8%A1%E0%B8%B0%E0%B8%88%E0%B8%B5%E0%B8%99" to "อนิเมะจีน",
            "/the-movie-ova" to "The Movie/OVA",
            "/Anime%E0%B8%97%E0%B8%B1%E0%B9%89%E0%B8%87%E0%B8%AB%E0%B8%A1%E0%B8%94" to "อนิเมะทั้งหมด",
        ),
    )
    // Preserve Suba's existing compact menu; other sites use their primary sections.
    val homeEntries = mapOf(
        Site.NEKO to entries.getValue(Site.NEKO).take(4),
        Site.RUKA to entries.getValue(Site.RUKA).take(5),
        Site.SUBA to entries.getValue(Site.SUBA),
    )
}

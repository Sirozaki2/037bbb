package com.moviedofree

// Movie categories captured from the website; series menus are omitted until episode loading is supported.
internal object SiteCategories {
    val entries = listOf(
        "/" to "หนังล่าสุด",
        "/%e0%b8%95%e0%b8%b1%e0%b8%a7%e0%b8%ad%e0%b8%a2%e0%b9%88%e0%b8%b2%e0%b8%87%e0%b8%ab%e0%b8%99%e0%b8%b1%e0%b8%87%e0%b9%83%e0%b8%ab%e0%b8%a1%e0%b9%88/" to "ตัวอย่างหนังใหม่",
        "/fillter-year?y=2025" to "ดูหนัง 2025",
        "/fillter-year?y=2024" to "ดูหนัง 2024",
        "/action-%e0%b8%ab%e0%b8%99%e0%b8%b1%e0%b8%87%e0%b9%81%e0%b8%ad%e0%b8%84%e0%b8%8a%e0%b8%b1%e0%b9%88%e0%b8%99/" to "Action บู้",
        "/animation-%e0%b9%81%e0%b8%ad%e0%b8%99%e0%b8%99%e0%b8%b4%e0%b9%80%e0%b8%a1%e0%b8%8a%e0%b8%b1%e0%b9%88%e0%b8%99/" to "Cartoon การ์ตูน",
        "/netflix/" to "หนัง Netflix",
        "/%e0%b8%ab%e0%b8%99%e0%b8%b1%e0%b8%87%e0%b8%88%e0%b8%b5%e0%b8%99/" to "หนังจีน",
        "/%e0%b8%ab%e0%b8%99%e0%b8%b1%e0%b8%87%e0%b8%8b%e0%b8%ad%e0%b8%a1%e0%b8%9a%e0%b8%b5%e0%b9%89/" to "หนังซอมบี้",
        "/%e0%b8%ab%e0%b8%99%e0%b8%b1%e0%b8%87%e0%b8%8d%e0%b8%b5%e0%b9%88%e0%b8%9b%e0%b8%b8%e0%b9%88%e0%b8%99/" to "หนังญี่ปุ่น",
        "/%e0%b8%ab%e0%b8%99%e0%b8%b1%e0%b8%87%e0%b8%9d%e0%b8%a3%e0%b8%b1%e0%b9%88%e0%b8%87/" to "หนังฝรั่ง",
        "/%e0%b8%ab%e0%b8%99%e0%b8%b1%e0%b8%87%e0%b8%ad%e0%b8%b4%e0%b8%99%e0%b9%80%e0%b8%94%e0%b8%b5%e0%b8%a2/" to "หนังอินเดีย",
        "/%e0%b8%ab%e0%b8%99%e0%b8%b1%e0%b8%87%e0%b9%80%e0%b8%81%e0%b8%b2%e0%b8%ab%e0%b8%a5%e0%b8%b5/" to "หนังเกาหลี",
        "/%e0%b8%ab%e0%b8%99%e0%b8%b1%e0%b8%87%e0%b9%84%e0%b8%97%e0%b8%a2/" to "หนังไทย",
        "/fillter-year?y=2023" to "2023",
        "/fillter-year?y=2022" to "2022",
        "/fillter-year?y=2021" to "2021",
        "/fillter-year?y=2020" to "2020",
        "/fillter-year?y=2019" to "2019",
        "/fillter-year?y=2018" to "2018",
        "/fillter-year?y=2017" to "2017",
        "/fillter-year?y=2016" to "2016",
        "/fillter-year?y=2015" to "2015",
        "/fillter-year?y=2014" to "2014",
        "/fillter-year?y=2013" to "2013",
        "/fillter-year?y=2012" to "2012",
        "/fillter-year?y=2011" to "2011",
        "/fillter-year?y=2010" to "2010",
        "/fillter-year?y=2009" to "2009",
        "/fillter-year?y=2008" to "2008",
        "/fillter-year?y=2007" to "2007",
        "/fillter-year?y=2006" to "2006",
        "/fillter-year?y=2005" to "2005",
        "/fillter-year?y=2004" to "2004",
        "/fillter-year?y=2003" to "2003",
        "/fillter-year?y=2002" to "2002",
        "/fillter-year?y=2001" to "2001",
        "/fillter-year?y=2000" to "2000",
        "/fillter-year?y=1999" to "1999",
        "/adventure-%e0%b8%9c%e0%b8%88%e0%b8%8d%e0%b8%a0%e0%b8%b1%e0%b8%a2/" to "Adventure ผจญภัย",
        "/biography-%e0%b8%8a%e0%b8%b5%e0%b8%a7%e0%b8%9b%e0%b8%a3%e0%b8%b0%e0%b8%a7%e0%b8%b1%e0%b8%95%e0%b8%b4/" to "Biography ชีวประวัติ",
        "/comedy-%e0%b8%95%e0%b8%a5%e0%b8%81/" to "Comedy ตลก",
        "/crime-%e0%b8%ad%e0%b8%b2%e0%b8%8a%e0%b8%8d%e0%b8%b2%e0%b8%81%e0%b8%a3%e0%b8%a3%e0%b8%a1/" to "Crime อาชญากรรม",
        "/documentary-%e0%b8%aa%e0%b8%b2%e0%b8%a3%e0%b8%84%e0%b8%94%e0%b8%b5/" to "Documentary สารคดี",
        "/drama-%e0%b8%ab%e0%b8%99%e0%b8%b1%e0%b8%87%e0%b8%94%e0%b8%a3%e0%b8%b2%e0%b8%a1%e0%b9%88%e0%b8%b2/" to "Drama ชีวิต",
        "/family-%e0%b8%84%e0%b8%a3%e0%b8%ad%e0%b8%9a%e0%b8%84%e0%b8%a3%e0%b8%b1%e0%b8%a7/" to "Family ครอบครัว",
        "/fantasy-%e0%b9%81%e0%b8%9f%e0%b8%99%e0%b8%95%e0%b8%b2%e0%b8%8b%e0%b8%b5/" to "Fantasy เทพนิยาย",
        "/history-%e0%b8%9b%e0%b8%a3%e0%b8%b0%e0%b8%a7%e0%b8%b1%e0%b8%95%e0%b8%b4%e0%b8%a8%e0%b8%b2%e0%b8%aa%e0%b8%95%e0%b8%a3%e0%b9%8c/" to "History ประวัติศาสตร์",
        "/horror-%e0%b8%ab%e0%b8%99%e0%b8%b1%e0%b8%87%e0%b8%aa%e0%b8%a2%e0%b8%ad%e0%b8%87%e0%b8%82%e0%b8%a7%e0%b8%b1%e0%b8%8d/" to "Horror สยองขวัญ",
        "/musical-%e0%b9%80%e0%b8%9e%e0%b8%a5%e0%b8%87%e0%b8%94%e0%b8%99%e0%b8%95%e0%b8%a3%e0%b8%b5/" to "Music เพลง",
        "/mystery-%e0%b8%a5%e0%b8%b6%e0%b8%81%e0%b8%a5%e0%b8%b1%e0%b8%9a%e0%b8%8b%e0%b9%88%e0%b8%ad%e0%b8%99%e0%b9%80%e0%b8%87%e0%b8%b7%e0%b9%88%e0%b8%ad%e0%b8%99/" to "Mystery ลึกลับซ่อนเงื่อน",
        "/romance-%e0%b9%82%e0%b8%a3%e0%b9%81%e0%b8%a1%e0%b8%99%e0%b8%95%e0%b8%b4%e0%b8%81/" to "Romance โรแมนติก",
        "/sci-fi-%e0%b8%a7%e0%b8%b4%e0%b8%97%e0%b8%a2%e0%b8%b2%e0%b8%a8%e0%b8%b2%e0%b8%aa%e0%b8%95%e0%b8%a3%e0%b9%8c/" to "Sci-Fi วิทยาศาสตร์",
        "/sport-%e0%b8%81%e0%b8%b5%e0%b8%ac%e0%b8%b2/" to "Sport กีฬา",
        "/thriller-%e0%b8%a3%e0%b8%b0%e0%b8%97%e0%b8%b6%e0%b8%81%e0%b8%82%e0%b8%a7%e0%b8%b1%e0%b8%8d/" to "Thriller ระทึกขวัญ",
        "/war-%e0%b8%aa%e0%b8%87%e0%b8%84%e0%b8%a3%e0%b8%b2%e0%b8%a1/" to "War สงคราม",
        "/western-%e0%b8%84%e0%b8%b2%e0%b8%a7%e0%b8%9a%e0%b8%ad%e0%b8%a2%e0%b8%95%e0%b8%b0%e0%b8%a7%e0%b8%b1%e0%b8%99%e0%b8%95%e0%b8%81/" to "Western คาวบอยตะวันตก"
    )

    // The trailer category currently reports no items; use populated movie categories.
    val homeEntries = listOf("หนังล่าสุด", "Action บู้", "Cartoon การ์ตูน", "หนังไทย").map { title ->
        entries.first { it.second == title }
    }
}

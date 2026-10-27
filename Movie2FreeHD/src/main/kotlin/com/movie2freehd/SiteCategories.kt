package com.movie2freehd

// Navigation observed on movie2freehd.com; movie and series archives paginate separately.
internal object SiteCategories {
    val entries = listOf(
        "/movies/" to "หนังล่าสุด",
        "/series/" to "ซีรีส์ล่าสุด",
        "/tag/nang-ok-mai" to "หนังออกใหม่",
        "/tag/nang-asia" to "หนังเอเชีย",
        "/tag/nang-farang" to "หนังฝรั่ง",
        "/tag/nang-pak-tor" to "หนังภาคต่อ",
        "/tag/nang-cartoon" to "หนังการ์ตูน",
        "/genre/หนังชีวิต" to "หนังชีวิต",
        "/genre/ตลก" to "ตลก",
        "/genre/บู๊" to "บู๊",
        "/genre/ระทึกขวัญ" to "ระทึกขวัญ",
        "/genre/หนังรักโรแมนติก" to "หนังรักโรแมนติก",
        "/genre/สยองขวัญ" to "สยองขวัญ",
        "/genre/อาชญากรรม" to "อาชญากรรม",
        "/genre/ผจญ" to "ผจญ",
        "/genre/จินตนาการ" to "จินตนาการ",
        "/genre/นิยายวิทยาศาสตร์" to "นิยายวิทยาศาสตร์",
        "/genre/ครอบครัว" to "ครอบครัว",
        "/genre/ลึกลับ" to "ลึกลับ",
        "/genre/แอนนิเมชั่น" to "แอนนิเมชั่น",
        "/genre/หนังฝรั่ง" to "หนังฝรั่ง",
        "/genre/สารคดี" to "สารคดี",
        "/genre/ประวัติศาสตร์" to "ประวัติศาสตร์",
        "/genre/ดนตรี" to "ดนตรี",
        "/genre/สงคราม" to "สงคราม",
        "/genre/ภาพยนตร์โทรทัศน์" to "ภาพยนตร์โทรทัศน์",
        "/genre/หนังเอเชีย" to "หนังเอเชีย",
        "/genre/หนังคาวบอยตะวันตก" to "หนังคาวบอยตะวันตก",
        "/genre/จิตนิมิตแนววิทยาศาสตร์" to "จิตนิมิตแนววิทยาศาสตร์",
        "/genre/หนังจีน" to "หนังจีน",
        "/genre/การ์ตูน" to "การ์ตูน",
        "/genre/บู๊-ผจญภัย" to "บู๊, ผจญภัย",
        "/genre/หนังเกาหลี" to "หนังเกาหลี",
        "/genre/หนังไทย" to "หนังไทย",
        "/genre/หนังญี่ปุ่น" to "หนังญี่ปุ่น",
        "/tag/ดูหนังออนไลน์" to "ดูหนังออนไลน์ฟรี",
        "/tag/หนังสนุก" to "ดูหนังสนุก",
        "/tag/พากย์ไทย" to "หนังพากย์ไทย",
        "/tag/ดูฟรี" to "หนังดีดูฟรี",
        "/tag/ดูหนังเต็มเรื่อง" to "ดูหนังเต็มเรื่อง",
        "/tag/เว็บดูหนัง" to "เว็บดูหนัง"
    )

    // Cloudstream waits for every home row; keep the initial request set small.
    val homeEntries = listOf(
        "/movies/" to "หนังล่าสุด",
        "/series/" to "ซีรีส์ล่าสุด",
        "/tag/nang-ok-mai" to "หนังออกใหม่",
        "/tag/nang-cartoon" to "หนังการ์ตูน",
    )
}

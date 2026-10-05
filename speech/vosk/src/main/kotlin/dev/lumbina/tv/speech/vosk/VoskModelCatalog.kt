package dev.lumbina.tv.speech.vosk

/**
 * A published Vosk model archive (a zip with one top-level folder), downloadable from any of
 * [urls]: the checksum makes mirrors as trustworthy as the original.
 */
internal data class VoskModel(val language: String, val name: String, val sizeBytes: Long, val sha256: String) {
    val urls: List<String>
        get() = listOf(
            "https://alphacephei.com/vosk/models/$name.zip",
            "https://huggingface.co/localstack/vosk-models/resolve/main/$name.zip",
        )
}

/** Vosk's small models (~35–110 MB, meant for phones and TVs): Russian and English, then by language code. */
internal object VoskModelCatalog {
    val models = listOf(
        VoskModel(
            language = "ru",
            name = "vosk-model-small-ru-0.22",
            sizeBytes = 46_236_750,
            sha256 = "961d5ff98a17f4aa6de69864d0aa71fa5bac682301d2b5d17a3f24c5c99a46d4",
        ),
        VoskModel(
            language = "en",
            name = "vosk-model-small-en-us-0.15",
            sizeBytes = 41_205_931,
            sha256 = "30f26242c4eb449f948e42cb302dd7a686cb29a3423a8367f99ff41780942498",
        ),
        VoskModel(
            language = "ca",
            name = "vosk-model-small-ca-0.4",
            sizeBytes = 43_405_881,
            sha256 = "99f90bfff5c2b187c705ddbc20aaa2600eddc359466f404404f1db6029fba5d4",
        ),
        VoskModel(
            language = "cs",
            name = "vosk-model-small-cs-0.4-rhasspy",
            sizeBytes = 46_088_666,
            sha256 = "287c3bbefc8ad67b4ab9636eecef3d62acc3719990777d03e226db5a7f19fbda",
        ),
        VoskModel(
            language = "de",
            name = "vosk-model-small-de-0.15",
            sizeBytes = 46_499_967,
            sha256 = "b7e53c90b1f0a38456f4cd62b366ecd58803cd97cd42b06438e2c131713d5e43",
        ),
        VoskModel(
            language = "es",
            name = "vosk-model-small-es-0.42",
            sizeBytes = 39_817_833,
            sha256 = "09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f",
        ),
        VoskModel(
            language = "fa",
            name = "vosk-model-small-fa-0.42",
            sizeBytes = 53_431_220,
            sha256 = "977cb5faa538f3a835ccfd35f5f6d8284b5c450b89c700b9bd4736b66536ad46",
        ),
        VoskModel(
            language = "fr",
            name = "vosk-model-small-fr-0.22",
            sizeBytes = 42_233_323,
            sha256 = "cabf6180e177eb9b3a9a9d43a437bd5e549f3a7d09525e5d69a3fed787be12ad",
        ),
        VoskModel(
            language = "gu",
            name = "vosk-model-small-gu-0.42",
            sizeBytes = 108_054_987,
            sha256 = "e87b7055e52b38a493a8308cf815b6fc05b9a89ef8aee18b4cbc2867cfc5f42a",
        ),
        VoskModel(
            language = "hi",
            name = "vosk-model-small-hi-0.22",
            sizeBytes = 44_458_845,
            sha256 = "7c50a10866889f0ac21d912c20537a055a597ed09fc1d3e5bcd798f9f0017e48",
        ),
        VoskModel(
            language = "it",
            name = "vosk-model-small-it-0.22",
            sizeBytes = 49_665_141,
            sha256 = "9ec65e75861d1c6c2e457cccd932705340dcdf233f5b239f00733b4de0bf3267",
        ),
        VoskModel(
            language = "ja",
            name = "vosk-model-small-ja-0.22",
            sizeBytes = 49_704_573,
            sha256 = "efa092d280153a77615e9e0c7d7283e93e600de3d19d3bec686c57ef19d52eac",
        ),
        VoskModel(
            language = "kk",
            name = "vosk-model-small-kz-0.15",
            sizeBytes = 43_739_114,
            sha256 = "b5eb54b400d1b66d1b017cadfa63725cec7359cb7d54528f6326d02689984c49",
        ),
        VoskModel(
            language = "ko",
            name = "vosk-model-small-ko-0.22",
            sizeBytes = 86_914_329,
            sha256 = "eea36124087fed26c59996a4761519458e3bd185e8ea9d9865ad8760c4a1d989",
        ),
        VoskModel(
            language = "nl",
            name = "vosk-model-small-nl-0.22",
            sizeBytes = 40_441_176,
            sha256 = "039811c3b829de64e4f123a9f684a53784005b212a346ac0b899dc7efce2ed0a",
        ),
        VoskModel(
            language = "pl",
            name = "vosk-model-small-pl-0.22",
            sizeBytes = 52_979_372,
            sha256 = "c4cd16498ea544f446f9e9a55cbd602b71cfe5a2b6f2b0834d81e1b6fce15f0d",
        ),
        VoskModel(
            language = "pt",
            name = "vosk-model-small-pt-0.3",
            sizeBytes = 32_453_112,
            sha256 = "6e1ce909032e1afa7a88e68a3d628ecafff302bdf195befab308826c395e93b7",
        ),
        VoskModel(
            language = "te",
            name = "vosk-model-small-te-0.42",
            sizeBytes = 60_544_249,
            sha256 = "3e77b05becc7599a596ccebd8b22cb95eb4eca0cfca524827cdfbfd66af09497",
        ),
        VoskModel(
            language = "tr",
            name = "vosk-model-small-tr-0.3",
            sizeBytes = 36_855_784,
            sha256 = "8c8d07cec1bce31add14967c16891c84152cdf76d391e33c08278119b9ea96e5",
        ),
        VoskModel(
            language = "uk",
            name = "vosk-model-small-uk-v3-nano",
            sizeBytes = 77_622_640,
            sha256 = "d6b50be0a2d20f891720f3a83c98290bb2d396fd666142dbf716e8ad302f8233",
        ),
        VoskModel(
            language = "uz",
            name = "vosk-model-small-uz-0.22",
            sizeBytes = 51_061_189,
            sha256 = "ffb77328df64f222a3fac6e368e0baff9dc1258f11b6b5ca5deaa82e5f688e6a",
        ),
        VoskModel(
            language = "vi",
            name = "vosk-model-small-vn-0.4",
            sizeBytes = 33_656_337,
            sha256 = "efe5c8494212110471a79befc48c79da679e5b1fc52a4ffb500222ff86d622e5",
        ),
        VoskModel(
            language = "zh",
            name = "vosk-model-small-cn-0.22",
            sizeBytes = 43_898_754,
            sha256 = "3af8b0e7e0f835ae9d414ce5df580237a3cfb08d586c9fbbb0f7ff29ad5b14ba",
        ),
    )

    fun model(language: String): VoskModel? = models.firstOrNull { it.language == language }
}

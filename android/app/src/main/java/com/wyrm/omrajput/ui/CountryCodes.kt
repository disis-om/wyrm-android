package com.wyrm.omrajput.ui

/**
 * A country and its dialling code.
 *
 * The player picks from this list rather than typing a prefix, so nobody has to
 * know that their number needs a +91 in front of it. The flag is the emoji pair
 * for the ISO code, built from the letters themselves — no image assets, and it
 * works for every country whether or not it is in this list.
 */
data class Country(
    val iso: String,
    val name: String,
    val dial: String,
) {
    val flag: String
        get() = iso.uppercase().map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }
            .joinToString("")
}

/**
 * The countries offered, most-likely first.
 *
 * Not exhaustive — it is the set worth scrolling rather than every dialling code
 * on earth. India leads because that is where the arena's players are.
 */
val COUNTRIES: List<Country> = listOf(
    Country("IN", "India", "+91"),
    Country("US", "United States", "+1"),
    Country("GB", "United Kingdom", "+44"),
    Country("CA", "Canada", "+1"),
    Country("AU", "Australia", "+61"),
    Country("AE", "United Arab Emirates", "+971"),
    Country("SA", "Saudi Arabia", "+966"),
    Country("PK", "Pakistan", "+92"),
    Country("BD", "Bangladesh", "+880"),
    Country("LK", "Sri Lanka", "+94"),
    Country("NP", "Nepal", "+977"),
    Country("SG", "Singapore", "+65"),
    Country("MY", "Malaysia", "+60"),
    Country("ID", "Indonesia", "+62"),
    Country("PH", "Philippines", "+63"),
    Country("TH", "Thailand", "+66"),
    Country("VN", "Vietnam", "+84"),
    Country("CN", "China", "+86"),
    Country("JP", "Japan", "+81"),
    Country("KR", "South Korea", "+82"),
    Country("DE", "Germany", "+49"),
    Country("FR", "France", "+33"),
    Country("IT", "Italy", "+39"),
    Country("ES", "Spain", "+34"),
    Country("PT", "Portugal", "+351"),
    Country("NL", "Netherlands", "+31"),
    Country("BE", "Belgium", "+32"),
    Country("SE", "Sweden", "+46"),
    Country("NO", "Norway", "+47"),
    Country("DK", "Denmark", "+45"),
    Country("FI", "Finland", "+358"),
    Country("PL", "Poland", "+48"),
    Country("CH", "Switzerland", "+41"),
    Country("AT", "Austria", "+43"),
    Country("IE", "Ireland", "+353"),
    Country("RU", "Russia", "+7"),
    Country("UA", "Ukraine", "+380"),
    Country("TR", "Turkey", "+90"),
    Country("EG", "Egypt", "+20"),
    Country("ZA", "South Africa", "+27"),
    Country("NG", "Nigeria", "+234"),
    Country("KE", "Kenya", "+254"),
    Country("GH", "Ghana", "+233"),
    Country("BR", "Brazil", "+55"),
    Country("AR", "Argentina", "+54"),
    Country("MX", "Mexico", "+52"),
    Country("CL", "Chile", "+56"),
    Country("CO", "Colombia", "+57"),
    Country("PE", "Peru", "+51"),
    Country("NZ", "New Zealand", "+64"),
    Country("IL", "Israel", "+972"),
    Country("QA", "Qatar", "+974"),
    Country("KW", "Kuwait", "+965"),
    Country("OM", "Oman", "+968"),
    Country("BH", "Bahrain", "+973"),
)

val DEFAULT_COUNTRY: Country = COUNTRIES.first()

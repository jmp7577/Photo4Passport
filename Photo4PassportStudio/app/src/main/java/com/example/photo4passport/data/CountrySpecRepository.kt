package com.example.photo4passport.data

class CountrySpecRepository {
    fun getAll(): List<CountrySpec> = listOf(
        // US: 2×2 inch (51×51 mm). Head must be between 1–1⅜ inches (25–35 mm), i.e. 49%–69% of 51 mm.
        // Source: https://travel.state.gov/content/travel/en/passports/how-apply/photos.html
        CountrySpec("US", "United States (2×2 in / 51×51 mm)", 51f, 51f, 0.49f, 0.69f, BackgroundMode.WHITE),

        // India: 35×45 mm. Face (chin to top of head) must cover 70–80% of the frame height.
        // Source: VFS Global India passport photo specification
        CountrySpec("IN", "India (35×45 mm)", 35f, 45f, 0.60f, 0.80f, BackgroundMode.WHITE),

        // UK: 35×45 mm. Head height (crown to chin) must be between 29–34 mm out of 45 mm = 64%–76%.
        // Background: plain light grey or cream (NOT light blue). Source: gov.uk/photos-for-passports
        CountrySpec("UK", "United Kingdom (35×45 mm)", 35f, 45f, 0.55f, 0.76f, BackgroundMode.WHITE),

        // Canada: 50×70 mm. Head height (top of hair to chin) must be 31–36 mm out of 70 mm = 44%–51%.
        // Source: canada.ca passport photo requirements
        CountrySpec("CA", "Canada (50×70 mm)", 50f, 70f, 0.4f, 0.51f, BackgroundMode.WHITE)
    )

    fun defaultCountry(): CountrySpec = getAll().first()
}

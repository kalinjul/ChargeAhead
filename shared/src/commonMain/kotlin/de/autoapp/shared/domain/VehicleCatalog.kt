package de.autoapp.shared.domain

/**
 * Common EV models as presets for the garage.
 *
 * ROADMAP open item 4 names the trade-off: every facelift makes an entry
 * stale, and a wrong preset is worse than none because nobody double-checks
 * it. The presets are therefore only a starting point — battery and
 * consumption land in an editable [VehicleProfile], and consumption in
 * particular is meant to be adjusted per driver (nobody drives the WLTP value).
 */
data class VehiclePreset(
    val name: String,
    val usableBatteryKwh: Double,
    val consumptionKwhPer100Km: Double,
    val dcPeakPowerKw: Double,
) {
    fun toProfile(): VehicleProfile = VehicleProfile(
        displayName = name,
        usableBatteryKwh = usableBatteryKwh,
        consumptionKwhPer100Km = consumptionKwhPer100Km,
        // CCS is the fast-charging standard in Europe; presets that need
        // something else (older Leaf: CHAdeMO) don't belong in this list.
        acceptedConnectors = setOf(ConnectorType.CCS2),
        dcPeakPowerKw = dcPeakPowerKw,
    )
}

object VehicleCatalog {

    val all: List<VehiclePreset> = listOf(
        // VehiclePreset(<Name>, <nutzbare Batterie kWh>, <Verbrauch WLTP kWh/100km>, <DC-Spitze kW>.0),  // <Anschluss>, <Reichweite WLTP>
        // Werte sind Herstellerangaben (WLTP) bzw. gängige Datenbankwerte, gerundet. DC 0 = kein DC-Anschluss.

        // ---------- Tesla ----------
        VehiclePreset("Tesla Model S 100D (2017)", 95.0, 19.0, 150.0),                 // CCS (nachgerüstet) / Tesla, 632 km
        VehiclePreset("Tesla Model S Long Range (2021+)", 95.0, 17.5, 250.0),          // CCS, 634 km
        VehiclePreset("Tesla Model S Plaid", 95.0, 18.7, 250.0),                       // CCS, 600 km
        VehiclePreset("Tesla Model X Long Range (2021+)", 95.0, 20.6, 250.0),          // CCS, 576 km
        VehiclePreset("Tesla Model 3 Standard Range+ (2019)", 50.0, 14.9, 170.0),      // CCS, 409 km
        VehiclePreset("Tesla Model 3 RWD LFP (2021)", 57.5, 14.0, 170.0),              // CCS, 491 km
        VehiclePreset("Tesla Model 3 Long Range AWD (2019)", 75.0, 16.0, 250.0),       // CCS, 560 km
        VehiclePreset("Tesla Model 3 Performance (2019)", 75.0, 16.5, 250.0),          // CCS, 530 km
        VehiclePreset("Tesla Model 3 RWD Highland (2024)", 57.5, 13.2, 170.0),         // CCS, 513 km
        VehiclePreset("Tesla Model 3 LR RWD Highland (2024)", 75.0, 12.5, 250.0),      // CCS, 702 km
        VehiclePreset("Tesla Model 3 LR AWD Highland (2024)", 75.0, 14.0, 250.0),      // CCS, 629 km
        VehiclePreset("Tesla Model 3 Performance Highland (2024)", 75.0, 16.7, 250.0), // CCS, 528 km
        VehiclePreset("Tesla Model Y RWD LFP (2022)", 57.5, 15.6, 170.0),              // CCS, 455 km
        VehiclePreset("Tesla Model Y Long Range AWD (2022)", 75.0, 16.9, 250.0),       // CCS, 533 km
        VehiclePreset("Tesla Model Y Performance (2022)", 75.0, 17.4, 250.0),          // CCS, 514 km
        VehiclePreset("Tesla Model Y LR RWD Juniper (2025)", 75.0, 13.9, 250.0),       // CCS, 622 km
        VehiclePreset("Tesla Model Y LR AWD Juniper (2025)", 75.0, 14.6, 250.0),       // CCS, 586 km
        VehiclePreset("Tesla Model Y Performance Juniper (2025)", 75.0, 15.9, 250.0),  // CCS, 580 km

// ---------- Volkswagen ----------
        VehiclePreset("VW e-Golf (2017)", 32.0, 13.8, 40.0),                           // CCS, 231 km
        VehiclePreset("VW e-up! (2020)", 32.3, 12.7, 40.0),                            // CCS, 260 km
        VehiclePreset("VW ID.3 Pure (45 kWh)", 45.0, 14.5, 100.0),                     // CCS, 350 km
        VehiclePreset("VW ID.3 Pro (58 kWh)", 58.0, 15.5, 120.0),                      // CCS, 426 km
        VehiclePreset("VW ID.3 Pro S (77 kWh)", 77.0, 15.5, 170.0),                    // CCS, 546 km
        VehiclePreset("VW ID.3 Pro (59 kWh, 2024)", 59.0, 14.9, 165.0),                // CCS, 434 km
        VehiclePreset("VW ID.3 GTX Performance", 79.0, 15.6, 185.0),                   // CCS, 601 km
        VehiclePreset("VW ID.4 Pure (52 kWh)", 52.0, 16.3, 115.0),                     // CCS, 346 km
        VehiclePreset("VW ID.4 Pro (77 kWh, 2021)", 77.0, 16.3, 135.0),                // CCS, 522 km
        VehiclePreset("VW ID.4 Pro (77 kWh, 2024)", 77.0, 15.9, 175.0),                // CCS, 550 km
        VehiclePreset("VW ID.4 GTX (2024)", 77.0, 17.3, 175.0),                        // CCS, 517 km
        VehiclePreset("VW ID.5 Pro (2024)", 77.0, 15.8, 175.0),                        // CCS, 556 km
        VehiclePreset("VW ID.7 Pro", 77.0, 14.1, 175.0),                               // CCS, 621 km
        VehiclePreset("VW ID.7 Pro S", 86.0, 13.6, 200.0),                             // CCS, 709 km
        VehiclePreset("VW ID.7 GTX", 86.0, 15.5, 200.0),                               // CCS, 595 km
        VehiclePreset("VW ID. Buzz (82 kWh)", 77.0, 20.6, 170.0),                      // CCS, 423 km
        VehiclePreset("VW ID. Buzz LWB (91 kWh)", 86.0, 20.2, 200.0),                  // CCS, 487 km

// ---------- Skoda ----------
        VehiclePreset("Skoda Citigo-e iV", 32.3, 12.9, 40.0),                          // CCS, 260 km
        VehiclePreset("Skoda Enyaq 60", 58.0, 15.9, 120.0),                            // CCS, 412 km
        VehiclePreset("Skoda Enyaq 80", 77.0, 15.8, 135.0),                            // CCS, 534 km
        VehiclePreset("Skoda Enyaq 85 (2024)", 77.0, 15.0, 175.0),                     // CCS, 580 km
        VehiclePreset("Skoda Enyaq 85x (2024)", 77.0, 16.0, 175.0),                    // CCS, 550 km
        VehiclePreset("Skoda Enyaq RS", 77.0, 16.9, 175.0),                            // CCS, 534 km
        VehiclePreset("Skoda Enyaq Coupe 85", 77.0, 14.6, 175.0),                      // CCS, 590 km
        VehiclePreset("Skoda Elroq 50", 52.0, 15.5, 145.0),                            // CCS, 375 km
        VehiclePreset("Skoda Elroq 60", 59.0, 15.2, 165.0),                            // CCS, 400 km
        VehiclePreset("Skoda Elroq 85", 77.0, 15.0, 175.0),                            // CCS, 580 km

// ---------- Cupra / Seat ----------
        VehiclePreset("Seat Mii electric", 32.3, 12.9, 40.0),                          // CCS, 260 km
        VehiclePreset("Cupra Born 58", 58.0, 15.3, 120.0),                             // CCS, 424 km
        VehiclePreset("Cupra Born 77", 77.0, 15.5, 170.0),                             // CCS, 548 km
        VehiclePreset("Cupra Born 59 (2024)", 59.0, 15.0, 165.0),                      // CCS, 425 km
        VehiclePreset("Cupra Born VZ", 79.0, 15.7, 185.0),                             // CCS, 599 km
        VehiclePreset("Cupra Tavascan Endurance", 77.0, 15.4, 135.0),                  // CCS, 568 km
        VehiclePreset("Cupra Tavascan VZ", 77.0, 16.5, 135.0),                         // CCS, 522 km

// ---------- Audi ----------
        VehiclePreset("Audi e-tron 50 quattro", 64.7, 22.0, 120.0),                    // CCS, 341 km
        VehiclePreset("Audi e-tron 55 quattro", 86.5, 22.5, 150.0),                    // CCS, 436 km
        VehiclePreset("Audi Q8 e-tron 50", 89.0, 21.0, 150.0),                         // CCS, 491 km
        VehiclePreset("Audi Q8 e-tron 55", 106.0, 20.6, 170.0),                        // CCS, 582 km
        VehiclePreset("Audi Q4 e-tron 35", 52.0, 17.0, 110.0),                         // CCS, 341 km
        VehiclePreset("Audi Q4 e-tron 40", 77.0, 17.5, 125.0),                         // CCS, 520 km
        VehiclePreset("Audi Q4 e-tron 45 (2024)", 77.0, 16.7, 175.0),                  // CCS, 562 km
        VehiclePreset("Audi Q4 e-tron 55 quattro (2024)", 77.0, 17.4, 175.0),          // CCS, 540 km
        VehiclePreset("Audi e-tron GT quattro", 84.0, 19.6, 270.0),                    // CCS, 488 km
        VehiclePreset("Audi RS e-tron GT", 84.0, 20.2, 270.0),                         // CCS, 472 km
        VehiclePreset("Audi Q6 e-tron (83 kWh)", 75.8, 16.2, 225.0),                   // CCS, 545 km
        VehiclePreset("Audi Q6 e-tron performance", 94.9, 16.0, 270.0),                // CCS, 641 km
        VehiclePreset("Audi Q6 e-tron quattro", 94.9, 17.0, 270.0),                    // CCS, 625 km
        VehiclePreset("Audi A6 e-tron performance", 94.9, 14.0, 270.0),                // CCS, 756 km
        VehiclePreset("Audi A6 e-tron quattro", 94.9, 15.7, 270.0),                    // CCS, 720 km

// ---------- Porsche ----------
        VehiclePreset("Porsche Taycan PB+ (2020)", 83.7, 23.0, 270.0),                 // CCS, 484 km
        VehiclePreset("Porsche Taycan 4S PB+ (2020)", 83.7, 24.0, 270.0),              // CCS, 464 km
        VehiclePreset("Porsche Taycan Turbo S (2020)", 83.7, 24.5, 270.0),             // CCS, 416 km
        VehiclePreset("Porsche Taycan PB+ (2024)", 97.0, 19.0, 320.0),                 // CCS, 678 km
        VehiclePreset("Porsche Taycan Turbo (2024)", 97.0, 20.2, 320.0),               // CCS, 630 km
        VehiclePreset("Porsche Macan Electric", 95.0, 18.4, 270.0),                    // CCS, 641 km
        VehiclePreset("Porsche Macan 4 Electric", 95.0, 19.6, 270.0),                  // CCS, 613 km
        VehiclePreset("Porsche Macan Turbo Electric", 95.0, 20.7, 270.0),              // CCS, 591 km

// ---------- BMW / Mini ----------
        VehiclePreset("BMW i3 94Ah (2016)", 27.2, 13.1, 50.0),                         // CCS, 300 km
        VehiclePreset("BMW i3 120Ah (2019)", 37.9, 13.1, 50.0),                        // CCS, 310 km
        VehiclePreset("BMW iX3 (2021)", 74.0, 18.5, 150.0),                            // CCS, 460 km
        VehiclePreset("BMW i4 eDrive35", 66.0, 16.5, 180.0),                           // CCS, 483 km
        VehiclePreset("BMW i4 eDrive40", 80.7, 16.1, 205.0),                           // CCS, 590 km
        VehiclePreset("BMW i4 M50", 80.7, 18.0, 205.0),                                // CCS, 520 km
        VehiclePreset("BMW iX1 eDrive20", 64.7, 15.4, 130.0),                          // CCS, 475 km
        VehiclePreset("BMW iX1 xDrive30", 64.7, 16.8, 130.0),                          // CCS, 440 km
        VehiclePreset("BMW iX2 xDrive30", 64.7, 16.3, 130.0),                          // CCS, 449 km
        VehiclePreset("BMW iX xDrive40", 71.0, 19.4, 150.0),                           // CCS, 425 km
        VehiclePreset("BMW iX xDrive50", 105.2, 19.8, 195.0),                          // CCS, 630 km
        VehiclePreset("BMW iX M60", 105.2, 21.6, 195.0),                               // CCS, 566 km
        VehiclePreset("BMW iX xDrive45 (2025)", 94.8, 18.0, 175.0),                    // CCS, 602 km
        VehiclePreset("BMW iX xDrive60 (2025)", 109.1, 17.7, 195.0),                   // CCS, 701 km
        VehiclePreset("BMW i5 eDrive40", 81.2, 15.9, 205.0),                           // CCS, 582 km
        VehiclePreset("BMW i5 Touring eDrive40", 81.2, 16.1, 205.0),                   // CCS, 560 km
        VehiclePreset("BMW i5 M60 xDrive", 81.2, 18.2, 205.0),                         // CCS, 516 km
        VehiclePreset("BMW i7 xDrive60", 101.7, 18.4, 195.0),                          // CCS, 625 km
        VehiclePreset("BMW iX3 50 xDrive Neue Klasse (2026)", 108.7, 15.1, 400.0),     // CCS, 805 km
        VehiclePreset("Mini Cooper SE (2020)", 28.9, 15.2, 50.0),                      // CCS, 234 km
        VehiclePreset("Mini Cooper E (2024)", 36.6, 14.3, 75.0),                       // CCS, 305 km
        VehiclePreset("Mini Cooper SE (2024)", 49.2, 14.1, 95.0),                      // CCS, 402 km
        VehiclePreset("Mini Aceman E", 38.5, 14.7, 75.0),                              // CCS, 310 km
        VehiclePreset("Mini Aceman SE", 49.2, 14.7, 95.0),                             // CCS, 406 km
        VehiclePreset("Mini Countryman E", 64.7, 16.4, 130.0),                         // CCS, 462 km
        VehiclePreset("Mini Countryman SE ALL4", 64.7, 17.4, 130.0),                   // CCS, 433 km

// ---------- Mercedes-Benz / smart ----------
        VehiclePreset("Mercedes EQC 400", 80.0, 21.5, 110.0),                          // CCS, 417 km
        VehiclePreset("Mercedes EQA 250", 66.5, 17.7, 100.0),                          // CCS, 426 km
        VehiclePreset("Mercedes EQA 250+", 70.5, 15.7, 100.0),                         // CCS, 560 km
        VehiclePreset("Mercedes EQB 300 4MATIC", 66.5, 18.1, 100.0),                   // CCS, 423 km
        VehiclePreset("Mercedes EQT 200", 45.0, 18.9, 80.0),                           // CCS, 282 km
        VehiclePreset("Mercedes EQV 300", 90.0, 26.5, 110.0),                          // CCS, 363 km
        VehiclePreset("Mercedes EQE 350+", 90.6, 16.9, 170.0),                         // CCS, 639 km
        VehiclePreset("Mercedes EQE 350 4MATIC", 90.6, 18.3, 170.0),                   // CCS, 590 km
        VehiclePreset("Mercedes EQE SUV 500 4MATIC", 90.6, 19.7, 170.0),               // CCS, 547 km
        VehiclePreset("Mercedes EQS 450+ (2022)", 107.8, 16.5, 200.0),                 // CCS, 782 km
        VehiclePreset("Mercedes EQS 450+ (2024)", 118.0, 16.6, 200.0),                 // CCS, 822 km
        VehiclePreset("Mercedes EQS 580 4MATIC", 107.8, 18.5, 200.0),                  // CCS, 700 km
        VehiclePreset("Mercedes EQS SUV 450 4MATIC", 107.8, 20.5, 200.0),              // CCS, 610 km
        VehiclePreset("Mercedes CLA 250+ EQ (2025)", 85.0, 12.2, 320.0),               // CCS (800V.0), 792 km
        VehiclePreset("Mercedes CLA 350 4MATIC EQ (2025)", 85.0, 13.0, 320.0),         // CCS (800V.0), 771 km
        VehiclePreset("Mercedes GLC 400 4MATIC EQ (2026)", 94.0, 16.0, 330.0),         // CCS (800V.0), 713 km
        VehiclePreset("smart #1 Pro", 49.0, 17.5, 130.0),                              // CCS, 310 km
        VehiclePreset("smart #1 Pro+", 62.0, 17.6, 150.0),                             // CCS, 420 km
        VehiclePreset("smart #1 Brabus", 62.0, 19.4, 150.0),                           // CCS, 400 km
        VehiclePreset("smart #3 Pro+", 62.0, 16.3, 150.0),                             // CCS, 455 km
        VehiclePreset("smart #5 Pro (76 kWh LFP)", 74.4, 18.8, 150.0),                 // CCS, 465 km
        VehiclePreset("smart #5 Premium (100 kWh)", 94.0, 18.1, 400.0),                // CCS (800V.0), 590 km

// ---------- Hyundai / Kia / Genesis ----------
        VehiclePreset("Hyundai Ioniq Electric (2017)", 28.0, 11.5, 70.0),              // CCS, 280 km
        VehiclePreset("Hyundai Ioniq Electric (2020)", 38.3, 13.8, 50.0),              // CCS, 311 km
        VehiclePreset("Hyundai Kona Electric 39 (2018)", 39.2, 14.3, 44.0),            // CCS, 289 km
        VehiclePreset("Hyundai Kona Electric 64 (2018)", 64.0, 14.7, 77.0),            // CCS, 449 km
        VehiclePreset("Hyundai Kona Electric 48 (2023)", 48.4, 14.6, 74.0),            // CCS, 377 km
        VehiclePreset("Hyundai Kona Electric 65 (2023)", 65.4, 14.7, 102.0),           // CCS, 514 km
        VehiclePreset("Hyundai Inster Standard", 42.0, 14.3, 73.0),                    // CCS, 327 km
        VehiclePreset("Hyundai Inster Long Range", 49.0, 14.3, 85.0),                  // CCS, 370 km
        VehiclePreset("Hyundai Ioniq 5 58 kWh", 58.0, 16.7, 175.0),                    // CCS (800V.0), 384 km
        VehiclePreset("Hyundai Ioniq 5 72.6 kWh RWD", 72.6, 16.8, 220.0),              // CCS (800V.0), 481 km
        VehiclePreset("Hyundai Ioniq 5 77.4 kWh RWD", 77.4, 16.7, 233.0),              // CCS (800V.0), 507 km
        VehiclePreset("Hyundai Ioniq 5 84 kWh RWD (2025)", 84.0, 16.7, 258.0),         // CCS (800V.0), 570 km
        VehiclePreset("Hyundai Ioniq 5 84 kWh AWD (2025)", 84.0, 17.4, 258.0),         // CCS (800V.0), 546 km
        VehiclePreset("Hyundai Ioniq 5 N", 84.0, 21.2, 238.0),                         // CCS (800V.0), 448 km
        VehiclePreset("Hyundai Ioniq 6 53 kWh", 53.0, 13.9, 175.0),                    // CCS (800V.0), 429 km
        VehiclePreset("Hyundai Ioniq 6 77.4 kWh RWD", 77.4, 14.3, 233.0),              // CCS (800V.0), 614 km
        VehiclePreset("Hyundai Ioniq 6 77.4 kWh AWD", 77.4, 15.1, 233.0),              // CCS (800V.0), 583 km
        VehiclePreset("Hyundai Ioniq 9", 110.3, 19.9, 233.0),                          // CCS (800V.0), 620 km
        VehiclePreset("Kia Soul EV (2015)", 27.0, 14.7, 70.0),                         // CHAdeMO, 212 km
        VehiclePreset("Kia e-Soul 64", 64.0, 15.7, 77.0),                              // CCS, 452 km
        VehiclePreset("Kia e-Niro 39", 39.2, 15.3, 44.0),                              // CCS, 289 km
        VehiclePreset("Kia e-Niro 64", 64.0, 15.9, 77.0),                              // CCS, 455 km
        VehiclePreset("Kia Niro EV (2022)", 64.8, 16.2, 72.0),                         // CCS, 460 km
        VehiclePreset("Kia EV3 58.3 kWh", 58.3, 14.9, 101.0),                          // CCS, 436 km
        VehiclePreset("Kia EV3 81.4 kWh", 81.4, 14.9, 128.0),                          // CCS, 605 km
        VehiclePreset("Kia EV4 58.3 kWh", 58.3, 14.4, 101.0),                          // CCS, 440 km
        VehiclePreset("Kia EV4 81.4 kWh", 81.4, 14.1, 128.0),                          // CCS, 625 km
        VehiclePreset("Kia EV5 81.4 kWh", 81.4, 16.9, 150.0),                          // CCS, 530 km
        VehiclePreset("Kia EV6 58 kWh RWD", 58.0, 16.5, 175.0),                        // CCS (800V.0), 394 km
        VehiclePreset("Kia EV6 77.4 kWh RWD", 77.4, 16.5, 233.0),                      // CCS (800V.0), 528 km
        VehiclePreset("Kia EV6 77.4 kWh AWD", 77.4, 17.2, 233.0),                      // CCS (800V.0), 506 km
        VehiclePreset("Kia EV6 84 kWh RWD (2024)", 84.0, 16.0, 258.0),                 // CCS (800V.0), 582 km
        VehiclePreset("Kia EV6 GT", 77.4, 20.6, 233.0),                                // CCS (800V.0), 424 km
        VehiclePreset("Kia EV9 RWD", 99.8, 20.2, 210.0),                               // CCS (800V.0), 563 km
        VehiclePreset("Kia EV9 AWD", 99.8, 22.8, 210.0),                               // CCS (800V.0), 505 km
        VehiclePreset("Genesis GV60 Premium", 77.4, 17.4, 233.0),                      // CCS (800V.0), 517 km
        VehiclePreset("Genesis GV60 Sport Plus", 77.4, 19.1, 233.0),                   // CCS (800V.0), 466 km
        VehiclePreset("Genesis Electrified GV70", 77.4, 19.2, 233.0),                  // CCS (800V.0), 455 km
        VehiclePreset("Genesis Electrified G80", 87.2, 19.1, 233.0),                   // CCS (800V.0), 520 km

// ---------- Nissan ----------
        VehiclePreset("Nissan Leaf 24 kWh (2016)", 21.3, 15.0, 50.0),                  // CHAdeMO, 199 km
        VehiclePreset("Nissan Leaf 30 kWh (2016)", 28.0, 15.0, 50.0),                  // CHAdeMO, 250 km
        VehiclePreset("Nissan Leaf 40 kWh (2018)", 39.0, 17.1, 50.0),                  // CHAdeMO, 270 km
        VehiclePreset("Nissan Leaf e+ 62 kWh", 59.0, 18.5, 100.0),                     // CHAdeMO, 385 km
        VehiclePreset("Nissan Leaf 52 kWh (2026)", 52.0, 15.5, 105.0),                 // CCS, 436 km
        VehiclePreset("Nissan Leaf 75 kWh (2026)", 75.0, 15.5, 150.0),                 // CCS, 604 km
        VehiclePreset("Nissan Ariya 63 kWh", 63.0, 17.7, 130.0),                       // CCS, 403 km
        VehiclePreset("Nissan Ariya 87 kWh", 87.0, 18.0, 130.0),                       // CCS, 533 km
        VehiclePreset("Nissan Ariya 87 kWh e-4orce", 87.0, 19.4, 130.0),               // CCS, 500 km
        VehiclePreset("Nissan Micra EV 40 kWh (2025)", 40.0, 15.7, 80.0),              // CCS, 317 km
        VehiclePreset("Nissan Micra EV 52 kWh (2025)", 52.0, 15.1, 100.0),             // CCS, 416 km

// ---------- Renault / Dacia ----------
        VehiclePreset("Renault Zoe ZE40 R110", 41.0, 13.3, 0.0),                       // Typ 2 AC 22kW, kein DC, 395 km
        VehiclePreset("Renault Zoe ZE50 R135", 52.0, 17.2, 50.0),                      // CCS (optional.0), 395 km
        VehiclePreset("Renault Twingo E-Tech (2026)", 27.5, 12.5, 50.0),               // CCS, 263 km
        VehiclePreset("Renault 5 E-Tech 40 kWh", 40.0, 14.9, 80.0),                    // CCS, 312 km
        VehiclePreset("Renault 5 E-Tech 52 kWh", 52.0, 14.7, 100.0),                   // CCS, 410 km
        VehiclePreset("Renault 4 E-Tech 52 kWh", 52.0, 15.1, 100.0),                   // CCS, 409 km
        VehiclePreset("Renault Megane E-Tech EV40", 40.0, 15.5, 85.0),                 // CCS, 300 km
        VehiclePreset("Renault Megane E-Tech EV60", 60.0, 15.8, 130.0),                // CCS, 470 km
        VehiclePreset("Renault Scenic E-Tech 60 kWh", 60.0, 16.0, 130.0),              // CCS, 430 km
        VehiclePreset("Renault Scenic E-Tech 87 kWh", 87.0, 16.7, 150.0),              // CCS, 625 km
        VehiclePreset("Renault Kangoo E-Tech", 45.0, 19.5, 80.0),                      // CCS, 285 km
        VehiclePreset("Dacia Spring Electric 45 (2021)", 26.8, 13.9, 30.0),            // CCS (optional.0), 230 km
        VehiclePreset("Dacia Spring Electric 65 (2024)", 26.8, 14.6, 30.0),            // CCS (optional.0), 225 km

// ---------- Stellantis: Peugeot / Opel / Citroen / DS / Fiat / Abarth / Jeep / Alfa / Lancia ----------
        VehiclePreset("Peugeot e-208 (50 kWh)", 46.3, 15.4, 100.0),                    // CCS, 340 km
        VehiclePreset("Peugeot e-208 (51 kWh, 2023)", 48.1, 12.9, 100.0),              // CCS, 433 km
        VehiclePreset("Peugeot e-2008 (50 kWh)", 46.3, 17.2, 100.0),                   // CCS, 320 km
        VehiclePreset("Peugeot e-2008 (54 kWh)", 51.0, 15.3, 100.0),                   // CCS, 406 km
        VehiclePreset("Peugeot e-308", 51.0, 15.1, 100.0),                             // CCS, 409 km
        VehiclePreset("Peugeot e-3008 73 kWh", 73.0, 16.4, 160.0),                     // CCS, 527 km
        VehiclePreset("Peugeot e-3008 Long Range 97 kWh", 97.0, 15.2, 160.0),          // CCS, 700 km
        VehiclePreset("Peugeot e-5008 73 kWh", 73.0, 16.9, 160.0),                     // CCS, 502 km
        VehiclePreset("Peugeot e-5008 Long Range 97 kWh", 97.0, 15.7, 160.0),          // CCS, 668 km
        VehiclePreset("Opel Ampera-e (2017)", 57.0, 14.5, 50.0),                       // CCS, 423 km
        VehiclePreset("Opel Corsa-e (50 kWh)", 46.3, 16.4, 100.0),                     // CCS, 337 km
        VehiclePreset("Opel Corsa Electric (51 kWh)", 48.1, 14.2, 100.0),              // CCS, 405 km
        VehiclePreset("Opel Mokka-e (50 kWh)", 46.3, 17.4, 100.0),                     // CCS, 324 km
        VehiclePreset("Opel Mokka Electric (54 kWh)", 51.0, 15.2, 100.0),              // CCS, 403 km
        VehiclePreset("Opel Astra Electric", 51.0, 14.9, 100.0),                       // CCS, 418 km
        VehiclePreset("Opel Frontera Electric 44 kWh", 44.0, 16.8, 100.0),             // CCS, 305 km
        VehiclePreset("Opel Frontera Electric Long Range", 54.0, 16.3, 100.0),         // CCS, 408 km
        VehiclePreset("Opel Grandland Electric 73 kWh", 73.0, 16.4, 160.0),            // CCS, 523 km
        VehiclePreset("Opel Grandland Electric 97 kWh", 97.0, 15.3, 160.0),            // CCS, 694 km
        VehiclePreset("Opel Combo-e", 45.0, 19.7, 100.0),                              // CCS, 280 km
        VehiclePreset("Citroen e-C3 (44 kWh)", 44.0, 16.7, 100.0),                     // CCS, 320 km
        VehiclePreset("Citroen e-C3 Aircross", 44.0, 17.4, 100.0),                     // CCS, 300 km
        VehiclePreset("Citroen e-C4 (50 kWh)", 46.3, 16.0, 100.0),                     // CCS, 350 km
        VehiclePreset("Citroen e-C4 (54 kWh)", 51.0, 14.8, 100.0),                     // CCS, 420 km
        VehiclePreset("Citroen e-Berlingo", 45.0, 19.5, 100.0),                        // CCS, 280 km
        VehiclePreset("DS 3 Crossback E-Tense (50 kWh)", 46.3, 16.5, 100.0),           // CCS, 320 km
        VehiclePreset("DS 3 E-Tense (54 kWh)", 51.0, 15.0, 100.0),                     // CCS, 404 km
        VehiclePreset("Fiat 500e 24 kWh", 21.3, 13.0, 50.0),                           // CCS, 190 km
        VehiclePreset("Fiat 500e 42 kWh", 37.3, 14.3, 85.0),                           // CCS, 320 km
        VehiclePreset("Fiat 600e", 51.0, 15.1, 100.0),                                 // CCS, 409 km
        VehiclePreset("Fiat Grande Panda Electric", 44.0, 16.4, 100.0),                // CCS, 320 km
        VehiclePreset("Abarth 500e", 37.3, 18.1, 85.0),                                // CCS, 265 km
        VehiclePreset("Abarth 600e", 51.0, 16.8, 100.0),                               // CCS, 334 km
        VehiclePreset("Jeep Avenger Electric", 51.0, 15.4, 100.0),                     // CCS, 400 km
        VehiclePreset("Alfa Romeo Junior Elettrica", 51.0, 15.0, 100.0),               // CCS, 410 km
        VehiclePreset("Lancia Ypsilon Elettrica", 51.0, 14.8, 100.0),                  // CCS, 403 km

// ---------- Volvo / Polestar ----------
        VehiclePreset("Volvo XC40 Recharge Twin (78 kWh)", 75.0, 21.8, 150.0),         // CCS, 418 km
        VehiclePreset("Volvo XC40 Recharge Single (69 kWh)", 67.0, 17.8, 130.0),       // CCS, 425 km
        VehiclePreset("Volvo EX40 Single ER (82 kWh)", 79.0, 16.5, 205.0),             // CCS, 570 km
        VehiclePreset("Volvo EX40 Twin (82 kWh)", 79.0, 18.0, 205.0),                  // CCS, 530 km
        VehiclePreset("Volvo EC40 Single ER", 79.0, 16.1, 205.0),                      // CCS, 581 km
        VehiclePreset("Volvo EX30 Single Motor (51 kWh LFP)", 49.0, 16.7, 134.0),      // CCS, 344 km
        VehiclePreset("Volvo EX30 Single Motor ER (69 kWh)", 65.0, 16.0, 153.0),       // CCS, 480 km
        VehiclePreset("Volvo EX30 Twin Performance", 65.0, 17.5, 153.0),               // CCS, 450 km
        VehiclePreset("Volvo EX90 Twin (111 kWh)", 107.0, 20.5, 250.0),                // CCS, 614 km
        VehiclePreset("Volvo ES90 Twin (106 kWh)", 102.0, 16.0, 350.0),                // CCS (800V.0), 700 km
        VehiclePreset("Polestar 2 Standard Range (2020)", 67.0, 17.1, 130.0),          // CCS, 478 km
        VehiclePreset("Polestar 2 Long Range Single (2024)", 79.0, 14.8, 205.0),       // CCS, 655 km
        VehiclePreset("Polestar 2 Long Range Dual (2024)", 79.0, 17.3, 205.0),         // CCS, 592 km
        VehiclePreset("Polestar 3 Long Range Single", 107.0, 17.4, 250.0),             // CCS, 706 km
        VehiclePreset("Polestar 3 Long Range Dual", 107.0, 20.4, 250.0),               // CCS, 631 km
        VehiclePreset("Polestar 4 Long Range Single", 94.0, 17.8, 200.0),              // CCS, 620 km
        VehiclePreset("Polestar 4 Long Range Dual", 94.0, 19.0, 200.0),                // CCS, 590 km

// ---------- Ford ----------
        VehiclePreset("Ford Focus Electric (2017)", 33.5, 16.0, 50.0),                 // CCS, 225 km
        VehiclePreset("Ford Mustang Mach-E SR RWD", 70.0, 16.5, 115.0),                // CCS, 440 km
        VehiclePreset("Ford Mustang Mach-E ER RWD", 91.0, 16.0, 150.0),                // CCS, 600 km
        VehiclePreset("Ford Mustang Mach-E ER AWD", 91.0, 17.0, 150.0),                // CCS, 550 km
        VehiclePreset("Ford Mustang Mach-E GT", 91.0, 20.0, 150.0),                    // CCS, 490 km
        VehiclePreset("Ford Explorer EV Standard Range", 52.0, 16.0, 145.0),           // CCS, 375 km
        VehiclePreset("Ford Explorer EV Extended Range RWD", 77.0, 14.5, 135.0),       // CCS, 602 km
        VehiclePreset("Ford Explorer EV Extended Range AWD", 79.0, 16.5, 185.0),       // CCS, 566 km
        VehiclePreset("Ford Capri Extended Range RWD", 77.0, 14.3, 135.0),             // CCS, 627 km
        VehiclePreset("Ford Puma Gen-E", 43.0, 13.1, 100.0),                           // CCS, 376 km
        VehiclePreset("Ford E-Transit Custom", 64.0, 27.0, 125.0),                     // CCS, 337 km

// ---------- Toyota / Lexus / Subaru / Mazda / Honda ----------
        VehiclePreset("Toyota bZ4X FWD", 64.0, 14.4, 150.0),                           // CCS, 516 km
        VehiclePreset("Toyota bZ4X AWD", 64.0, 16.0, 150.0),                           // CCS, 470 km
        VehiclePreset("Lexus UX 300e (54 kWh)", 54.3, 16.8, 50.0),                     // CHAdeMO, 315 km
        VehiclePreset("Lexus UX 300e (72 kWh)", 72.8, 16.1, 50.0),                     // CHAdeMO, 450 km
        VehiclePreset("Lexus RZ 450e", 64.0, 16.8, 150.0),                             // CCS, 440 km
        VehiclePreset("Subaru Solterra", 64.0, 16.0, 150.0),                           // CCS, 465 km
        VehiclePreset("Mazda MX-30", 30.0, 19.0, 50.0),                                // CCS, 200 km
        VehiclePreset("Mazda 6e Standard (68.8 kWh)", 68.8, 16.6, 165.0),              // CCS, 479 km
        VehiclePreset("Mazda 6e Long Range (80 kWh)", 80.0, 17.0, 95.0),               // CCS, 552 km
        VehiclePreset("Honda e", 28.5, 17.2, 50.0),                                    // CCS, 222 km
        VehiclePreset("Honda e:Ny1", 61.9, 18.2, 78.0),                                // CCS, 412 km

// ---------- Jaguar / Lotus / Maserati / Rolls-Royce / Lucid / Fisker ----------
        VehiclePreset("Jaguar I-Pace EV400", 84.7, 22.0, 104.0),                       // CCS, 470 km
        VehiclePreset("Lotus Eletre", 109.0, 21.5, 350.0),                             // CCS (800V.0), 600 km
        VehiclePreset("Maserati Grecale Folgore", 96.0, 22.5, 150.0),                  // CCS, 501 km
        VehiclePreset("Rolls-Royce Spectre", 102.0, 22.4, 195.0),                      // CCS, 530 km
        VehiclePreset("Lucid Air Pure RWD", 88.0, 14.8, 250.0),                        // CCS, 747 km
        VehiclePreset("Lucid Air Grand Touring", 112.0, 14.9, 300.0),                  // CCS, 960 km
        VehiclePreset("Fisker Ocean Extreme", 106.0, 18.4, 200.0),                     // CCS, 707 km

// ---------- MG ----------
        VehiclePreset("MG ZS EV (44.5 kWh, 2019)", 42.5, 17.8, 76.0),                  // CCS, 263 km
        VehiclePreset("MG ZS EV Standard Range (51 kWh LFP)", 49.0, 17.3, 76.0),       // CCS, 320 km
        VehiclePreset("MG ZS EV Long Range (72 kWh)", 68.3, 17.8, 92.0),               // CCS, 440 km
        VehiclePreset("MG5 Electric Standard Range", 46.4, 17.3, 87.0),                // CCS, 320 km
        VehiclePreset("MG5 Electric Long Range", 57.4, 17.5, 87.0),                    // CCS, 400 km
        VehiclePreset("MG Marvel R", 65.0, 19.4, 92.0),                                // CCS, 402 km
        VehiclePreset("MG4 Standard (51 kWh LFP)", 50.8, 17.0, 88.0),                  // CCS, 350 km
        VehiclePreset("MG4 Comfort/Luxury (64 kWh)", 61.7, 16.6, 135.0),               // CCS, 450 km
        VehiclePreset("MG4 Extended Range (77 kWh)", 74.4, 16.5, 144.0),               // CCS, 520 km
        VehiclePreset("MG4 XPower", 61.7, 18.0, 135.0),                                // CCS, 385 km
        VehiclePreset("MG4 Long Range (64 kWh LFP, MJ 2026)", 61.0, 16.5, 154.0),      // CCS, 452 km
        VehiclePreset("MG4 EV Urban (43 kWh LFP)", 43.0, 15.5, 82.0),                  // CCS, 325 km
        VehiclePreset("MG4 EV Urban (54 kWh LFP)", 54.0, 15.6, 87.0),                  // CCS, 416 km
        VehiclePreset("MGS5 EV (49 kWh)", 47.1, 16.8, 120.0),                          // CCS, 340 km
        VehiclePreset("MGS5 EV (64 kWh)", 62.1, 16.1, 139.0),                          // CCS, 480 km
        VehiclePreset("MG Cyberster (77 kWh) RWD", 74.4, 18.6, 144.0),                 // CCS, 507 km

// ---------- BYD ----------
        VehiclePreset("BYD Dolphin Surf (30 kWh)", 30.0, 13.8, 65.0),                  // CCS, 220 km
        VehiclePreset("BYD Dolphin Surf (43.2 kWh)", 43.2, 14.5, 85.0),                // CCS, 322 km
        VehiclePreset("BYD Dolphin (44.9 kWh)", 44.9, 15.9, 65.0),                     // CCS, 340 km
        VehiclePreset("BYD Dolphin (60.4 kWh)", 60.4, 15.9, 88.0),                     // CCS, 427 km
        VehiclePreset("BYD Atto 3", 60.5, 15.6, 88.0),                                 // CCS, 420 km
        VehiclePreset("BYD Seal RWD", 82.5, 16.6, 150.0),                              // CCS, 570 km
        VehiclePreset("BYD Seal AWD", 82.5, 18.2, 150.0),                              // CCS, 520 km
        VehiclePreset("BYD Seal U (71.8 kWh)", 71.8, 19.9, 115.0),                     // CCS, 420 km
        VehiclePreset("BYD Seal U (87 kWh)", 87.0, 20.5, 140.0),                       // CCS, 500 km
        VehiclePreset("BYD Sealion 7 RWD", 82.5, 19.0, 150.0),                         // CCS, 482 km
        VehiclePreset("BYD Sealion 7 AWD", 91.3, 21.0, 230.0),                         // CCS, 502 km
        VehiclePreset("BYD Han", 85.4, 20.4, 120.0),                                   // CCS, 521 km
        VehiclePreset("BYD Tang", 108.8, 24.0, 170.0),                                 // CCS, 530 km

// ---------- Weitere chinesische Marken ----------
        VehiclePreset("NIO ET5 (75 kWh)", 73.0, 18.3, 140.0),                          // CCS, 456 km
        VehiclePreset("NIO ET5 (100 kWh)", 90.0, 18.3, 140.0),                         // CCS, 560 km
        VehiclePreset("NIO ET7 (100 kWh)", 90.0, 19.1, 140.0),                         // CCS, 580 km
        VehiclePreset("NIO EL7 (100 kWh)", 90.0, 21.1, 140.0),                         // CCS, 509 km
        VehiclePreset("XPeng G6 Standard Range RWD", 66.0, 17.5, 215.0),               // CCS (800V.0), 435 km
        VehiclePreset("XPeng G6 Long Range RWD", 87.5, 17.5, 280.0),                   // CCS (800V.0), 570 km
        VehiclePreset("XPeng G9 Long Range", 98.0, 19.0, 300.0),                       // CCS (800V.0), 570 km
        VehiclePreset("Zeekr X Long Range", 66.0, 17.5, 150.0),                        // CCS, 440 km
        VehiclePreset("Zeekr 001 Long Range", 94.0, 17.6, 200.0),                      // CCS, 620 km
        VehiclePreset("Zeekr 7X Long Range (100 kWh)", 94.0, 17.0, 360.0),             // CCS (800V.0), 615 km
        VehiclePreset("Leapmotor T03", 37.3, 16.3, 48.0),                              // CCS, 265 km
        VehiclePreset("Leapmotor B10 (56 kWh)", 56.2, 16.2, 100.0),                    // CCS, 361 km
        VehiclePreset("Leapmotor B10 (67 kWh)", 67.1, 16.2, 168.0),                    // CCS, 434 km
        VehiclePreset("Leapmotor C10", 69.9, 19.8, 84.0),                              // CCS, 420 km
        VehiclePreset("Ora Funky Cat (48 kWh)", 45.4, 16.5, 64.0),                     // CCS, 310 km
        VehiclePreset("Ora Funky Cat (63 kWh)", 59.3, 16.5, 67.0),                     // CCS, 420 km
        VehiclePreset("Aiways U5", 63.0, 17.0, 90.0)                                   // CCS, 410 km)
    )
}

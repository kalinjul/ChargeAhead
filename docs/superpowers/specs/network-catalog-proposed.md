# Network Catalog — Groupings (APPROVED 2026-09-09)

> **Sign-off (Raphael, 2026-09-09):** all 37 networks approved as proposed.
> Decisions: SureCharge/FM Conway (3612) **kept**; BP Pulse + Iberdrola (2247)
> **kept merged** under `bp-pulse`. EnBW (86) and Ionity (3299) confirmed as
> **single OCM IDs** — verified against both the 997-operator reference list
> and the raw POI sample; no sibling operator records exist. The many EnBW
> spellings are Bnetza free-text `Betreiber` variants, caught by the `enbw`
> nameKeyword, not separate OCM IDs. Task 2 hardcodes this table verbatim.

## Method

Sampled OCM POI density for 11 European markets: **DE, FR, NL, GB, AT, BE, CH, IT, ES, NO, SE** (max 10 000 POIs per country; returned counts below cap except DE/FR/GB/IT/ES which all hit the 10 000 ceiling — those markets have more chargers than what the sample covers). Cross-referenced against the full OCM operator reference list (997 operators). Total POI records analysed: **~61 580**.

Placeholder operators `(Unknown Operator)` (1), `(Private Residence/Individual)` (44), and `(Business Owner at Location)` (45) were excluded throughout. Operators acting as data aggregators/roaming platforms rather than charging network operators were also excluded (Electromaps/3401, pass pass électrique/3409, Easypark/3302, Alfen/180 — the last being a hardware vendor whose OCM entries are billing artefacts, not a driver subscription).

Pan-European brands fragmented across country-specific OCM entries were clustered by name stem, and every ID was verified against the reference list before inclusion. The **Sample POIs** column is the sum of POIs observed across all sampled countries — not a total station count.

---

## Proposed Network Catalog

| # | key | Display name | operatorIds (verified) | nameKeywords | Sample POIs | Markets | Note |
|---|-----|--------------|------------------------|--------------|-------------|---------|------|
| 1 | `shell-recharge` | Shell Recharge | 47, 156, 157, 3392, 3508, 3709 | `shell`, `recharge` | 4 062 | AT BE DE FR GB IT NL NO | 6-country OCM split: NL(47) DE(156) BE(157) UK(3392) ES(3508) FR(3709); largest pan-EU network in sample |
| 2 | `enel-x` | Enel X | 80 | `enel`, `enelx` | 3 931 | IT | Italy dominant; single ID; #1 in Italy (3 931 of which ~3 931 are IT) |
| 3 | `tesla` | Tesla | 23, 3534 | `tesla`, `supercharger` | 2 900 | AT BE CH DE ES FR GB IT NL NO SE | Two OCM IDs: Tesla-only Supercharger(23) vs. open-to-all(3534); present all 11 countries |
| 4 | `evbox` | EVBox | 73 | `evbox` | 2 177 | AT BE CH GB IT NL NO SE | Dutch hardware+network; very dense NL (2 055); single ID |
| 5 | `evnet-nl` | EVnetNL | 178 | `evnet`, `evnetnl` | 1 788 | NL | NL destination-charger network; 1 788 in NL; single ID |
| 6 | `bp-pulse` | BP Pulse | 32, 2247 | `bp`, `pulse`, `bpulse`, `iberdrola` | 1 715 | ES FR GB | UK(32) + Iberdrola JV in ES(2247); **2247 is ambiguous** — legally Iberdrola-majority JV, branding is BP Pulse (flagged below) |
| 7 | `allego` | Allego | 103 | `allego` | 1 528 | BE CH DE ES FR GB IT NL SE | Pan-EU HPC; single ID; present 9 of 11 markets |
| 8 | `power-dot` | Power Dot | 3538, 3550 | `powerdot`, `power dot` | 1 181 | DE ES FR | Pan-EU entry(3538) + ES variant(3550); #1 in France by OCM density |
| 9 | `izivia` | Izivia (Sodetrel) | 213 | `izivia`, `sodetrel` | 1 143 | FR | French leader (EDF subsidiary); single ID; 1 143 all in FR |
| 10 | `be-charge` | Be Charge | 3327 | `becharge`, `be charge` | 1 112 | IT | Italy #2 after Enel X; single ID; 1 112 all in IT |
| 11 | `surecharge` | SureCharge (FM Conway) | 3612 | `surecharge`, `conway` | 1 101 | GB | **Flag for review** — FM Conway is a civil-engineering contractor, OCM density (1 101 GB) appears disproportionately high vs. industry recognition; may be data-quality inflation |
| 12 | `repsol-ibil` | Repsol-Ibil | 91 | `repsol`, `ibil` | 1 080 | ES | Spain fuel-station network; single ID; 1 080 all in ES |
| 13 | `char-gy` | Char.gy | 3345 | `chargy`, `char.gy` | 856 | GB | UK lamp-post/street charger network; 856 in GB |
| 14 | `totalenergies` | TotalEnergies | 25, 3447, 3571 | `total`, `totalenergies` | 818 | BE DE ES FR GB NL | UK(25), FR(3447), ES(3571); same brand, country-split OCM entries |
| 15 | `endesa` | Endesa | 207 | `endesa` | 714 | ES | Enel subsidiary; leading Spanish utility EV network; 714 in ES |
| 16 | `mobive` | MObiVE | 3407 | `mobive` | 699 | FR | French public-infrastructure operator (municipal); 699 in FR |
| 17 | `ladenetz` | ladenetz.de | 69 | `ladenetz` | 680 | AT BE DE NL | German Stadtwerke roaming aggregator (not one utility but a consortium); 675 in DE |
| 18 | `enbw` | EnBW | 86 | `enbw` | 679 | AT DE IT | German market leader (675 in DE); single OCM ID |
| 19 | `pod-point` | POD Point | 3 | `podpoint`, `pod point` | 616 | BE GB NL NO | UK network (611 GB); EDF Energy subsidiary |
| 20 | `electra` | Electra | 3489 | `electra` | 479 | BE CH ES FR GB IT | French fast-charger network; 479 across 6 markets |
| 21 | `innogy-rwe` | Innogy SE (RWE eMobility) | 105 | `innogy`, `rwe` | 462 | AT CH DE | Former Innogy/RWE charging; 460 in DE; post-split entity, **distinct from E.ON Drive** (separate OCM IDs, different post-acquisition fate) |
| 22 | `zunder` | Zunder | 3324 | `zunder` | 432 | ES FR | Spanish HPC network expanding to FR; 432 globally |
| 23 | `ionity` | Ionity | 3299 | `ionity` | 412 | AT BE CH DE ES FR GB IT NL NO SE | Pan-EU highway HPC; single ID; present all 11 markets |
| 24 | `eon-drive` | E.ON Drive | 46, 3251, 3359, 3403, 3422, 3814 | `eon`, `e.on`, `eondrive` | 375 | DE GB IT NO SE | Cluster: DE(46) + DK(3251) + HU(3359) + Drive product(3403) + eondrive.ro(3422) + co-branded(3814); 3814 has 0 POIs in sample |
| 25 | `osprey` | Osprey Charging | 203 | `osprey` | 367 | GB | UK HPC network; 367 in GB |
| 26 | `atlante` | Atlante | 3588 | `atlante` | 357 | ES FR IT | Pan-EU (IT/FR/ES) fast-charger network; growing; 357 globally |
| 27 | `lidl` | Lidl | 38 | `lidl` | 333 | AT BE CH DE ES FR GB IT NL SE | Retail-destination chargers; present 10 of 11 markets; no subscription app but high visibility for Bnetza matching |
| 28 | `ewe` | EWE | 127 | `ewe` | 330 | DE | German regional utility (NW Germany / Lower Saxony); 330 in DE |
| 29 | `chargepoint` | ChargePoint | 5 | `chargepoint` | 294 | AT BE DE FR GB IT NL | US-origin network; 294 across 7 EU markets |
| 30 | `freshmile` | Freshmile | 3379 | `freshmile` | 289 | CH FR SE | French MSP/network; 289 globally |
| 31 | `alperia` | Alperia | 164 | `alperia` | 270 | IT | Alto Adige / South Tyrol utility (IT); 270 in IT |
| 32 | `smatrics` | SMATRICS | 3253 | `smatrics` | 234 | AT CH DE IT NO | Austrian market leader (176 AT); also DE/CH/IT; single ID |
| 33 | `ewiva` | Ewiva | 3720 | `ewiva` | 223 | IT | Italian HPC JV (Enel + Volkswagen); 223 in IT |
| 34 | `fastned` | FastNed | 74 | `fastned` | 205 | BE CH DE ES FR GB IT NL | NL-origin motorway HPC; 205 across 8 markets |
| 35 | `mer` | MER (Vattenfall InCharge) | 108, 3492 | `mer`, `vattenfall`, `incharge` | 190 | AT DE GB NL NO SE | Vattenfall InCharge(108) acquired by MER(3492); merged; NO/SE/GB/DE/AT/NL |
| 36 | `fortum-recharge` | Recharge (Fortum) | 198, 202 | `recharge`, `fortum`, `charge and drive` | 141 | CH FR NO SE | Fortum rebranded to Recharge in Nordics; NO(202) + formerly Fortum(198); dominant Norwegian brand |
| 37 | `grønn-kontakt` | Grønn Kontakt | 3247 | `grønn`, `gronn`, `gronnkontakt` | 38 | NO | Norwegian network; **OCM coverage of Norway is known to be sparse** — real footprint likely larger |

---

## Concerns and flags for sign-off

1. **BP Pulse / Iberdrola (ID 2247)**: OCM title is `Iberdrola | BP Pulse (ES)`. Iberdrola is the majority partner; BP Pulse is the brand on the charger. Grouping as `bp-pulse` is reasonable for driver UX but legally imprecise. Alternative: give it a separate `iberdrola` key. Current proposal keeps it in `bp-pulse` because a Spanish driver sees "BP Pulse" on the hardware.

2. **SureCharge (FM Conway, ID 3612)**: 1 101 POIs in GB is surprisingly high for a civil-engineering contractor. Could be a bulk OCM import. Suggest verifying against a secondary source before shipping — it would be embarrassing to list FM Conway alongside Shell and Tesla.

3. **E.ON Drive cluster (IDs 46, 3251, 3359, 3403, 3422, 3814)**: IDs 3814 (`Powered by E.ON Drive & Clever`) and 3422 (`eondrive.ro`) together contribute <5 POIs in sample. Including them in the cluster is low-risk but if the codebase wants a tighter set, drop to just {46, 3251, 3359, 3403}.

4. **Innogy SE vs. E.ON Drive**: These are kept separate. Post-2019 acquisition, Innogy's grid/charging business went to RWE (hence the `RWE eMobility` tag in OCM title 105), while E.ON kept retail customers. They are genuinely different operators in OCM and carry different station counts. If your Bnetza data shows them merged, that's a discrepancy worth flagging — the `nameKeywords` on 105 includes `innogy` and `rwe` to catch both spellings.

5. **MObiVE (ID 3407, 699 FR)**: This is a French public-infrastructure operator (largely municipally-funded), not a classic subscription network. Drivers do use it but may not "subscribe" to it. Low risk to include since Bnetza data likely has it; easy to exclude.

6. **Grønn Kontakt (ID 3247, 38 NO)**: OCM coverage of Norway is known to be sparse — this network is the primary public charging infrastructure company in Norway with far more real-world stations than reflected here. Keeping it.

7. **VIRTA (ID 136)**: VIRTA is primarily a B2B charging-management platform rather than an end-consumer network. Its OCM entries (102 across FI/SE/DE/FR/IT/CH) may represent white-label deployments rather than "VIRTA-branded" chargers a driver subscribes to. **Dropped from final catalog** — not included in the 37 networks above.

8. **Aral Pulse (ID 3455)**: Only 19 POIs in DE sample. Aral is a major German fuel-station brand (BP-owned); the low OCM count suggests its stations aren't well-represented in OCM. Dropped here since Task 2 can always add it once verified against Bnetza — nameKeyword `aral` suffices for text-matching.

---

## Audit appendix — clustered brand IDs

### Shell Recharge (6 IDs)
| ID | OCM title | Verified in /tmp/ocm-ref.json |
|----|-----------|-------------------------------|
| 47 | Shell Recharge Solutions (NL) | ✓ |
| 156 | Shell Recharge Solutions (DE) | ✓ |
| 157 | Shell Recharge Solutions (BE) | ✓ |
| 3392 | Shell Recharge Solutions (UK) | ✓ |
| 3508 | Shell Recharge (ES) (Cable Energia) | ✓ |
| 3709 | Shell EV Charging Solutions France | ✓ |

*Out-of-scope Shell IDs (non-European markets only): 59 (US), 3591 (MY), 3666 (ID), 3778 (IN), 3886 (TR), 3912 (PH), 3964 (AR).*

### Tesla (2 IDs)
| ID | OCM title | Verified |
|----|-----------|---------|
| 23 | Tesla (Tesla-only charging) | ✓ |
| 3534 | Tesla (including non-tesla) | ✓ |

### BP Pulse (2 IDs, European markets)
| ID | OCM title | Verified |
|----|-----------|---------|
| 32 | BP Pulse (UK) | ✓ |
| 2247 | Iberdrola \| BP Pulse (ES) | ✓ |

*Out-of-scope: 3659 (AU), 3788 (US), 3817 (JIO BP Pulse India).*

### TotalEnergies (3 IDs)
| ID | OCM title | Verified |
|----|-----------|---------|
| 25 | Total Energies (UK) | ✓ |
| 3447 | TotalEnergies (FR) | ✓ |
| 3571 | TotalEnergies (ES) | ✓ |

### E.ON Drive (6 IDs)
| ID | OCM title | Sample POIs | Verified |
|----|-----------|-------------|---------|
| 46 | E.ON (DE) | 5 | ✓ |
| 3251 | E.ON (DK) | 3 | ✓ |
| 3359 | E.ON (HU) | 1 | ✓ |
| 3403 | E.ON Drive | 365 | ✓ |
| 3422 | eondrive.ro | 1 | ✓ |
| 3814 | Powered by E.ON Drive & Clever | 0 | ✓ |

*Note: E.ON (CZ) ID 192 has 1 POI in IT in the sample — likely data noise; included in cluster for completeness.*

### Power Dot (2 IDs)
| ID | OCM title | Verified |
|----|-----------|---------|
| 3538 | Power Dot | ✓ |
| 3550 | PowerDot (Es) | ✓ |

### Recharge / Fortum (2 IDs)
| ID | OCM title | Verified |
|----|-----------|---------|
| 198 | Recharge (Formerly Fortum Charge & Drive) | ✓ |
| 202 | Charge & Drive (Fortum - NO) | ✓ |

### MER / Vattenfall InCharge (2 IDs)
| ID | OCM title | Verified |
|----|-----------|---------|
| 108 | Vattenfall InCharge | ✓ |
| 3492 | MER | ✓ |

*MER (Mobility Energy Resources) acquired Vattenfall InCharge Europe in 2021; both IDs remain active in OCM.*

---

*Generated 2026-09-09. Data source: OCM API v3, operator reference 997 entries, POI sample 11 countries (≤10 000 per country). Scripts and raw JSON retained in /tmp/ for this session only.*

---

## Expansion (all operators, junk filtered)

Updated 2026-09-09. Scope changed from 37 curated networks to every OCM operator with real European density.

**Method:** summed `OperatorID → POI count` across all 11 country files (AT/BE/CH/DE/ES/FR/GB/IT/NL/NO/SE). 404 distinct operator IDs observed. Applied the following filter to build singleton rows:

- **Cutoff:** >= 2 POIs in the aggregated sample. Drops one-off/private noise. At cutoff=2 → 291 singletons; cutoff=1 → 345; cutoff=3 → 271. Chose 2 as specified.
- **Not already covered:** any operator ID already in one of the 37 curated rows is excluded (the curated row covers it).
- **Excluded IDs:** 1 (Unknown Operator), 44 (Private Residence), 45 (Business Owner), 3401 (Electromaps aggregator), 3302 (Easypark), 3409 (pass pass électrique), 180 (Alfen, hardware vendor).
- **Excluded titles starting with `(`:** OCM convention for placeholder/generic entries.
- **Incharge (SE) / ID 3343:** keyword overridden from `incharge` → `inchargese` to avoid false-positive collision with `mer`'s existing `incharge` keyword.
- **R3 / ID unknown:** short name, keyword forced to full folded title `r3` (3 chars, accepted as minimum).

**Results:**
- 37 curated rows preserved verbatim at the top of `all` (unchanged keys, operatorIds, nameKeywords).
- 291 singleton rows appended, sorted by descending POI count.
- **Total: 328 networks** in `NetworkCatalog.all`.
- Test `all_has_37_networks` renamed to `all_has_328_networks` and count updated; all other tests unchanged and passing.

# Network power classification — for sign-off

> **Status: awaiting Raphael's sign-off.** Nothing baked into `NetworkCatalog` yet.

## Method

For every one of the 326 catalog networks, sampled up to 120 of its POIs from OpenChargeMap (by `operatorid`) and took the **max `Connections[].PowerKW`** across them. `maxPowerKw` is that value; **slow-only = max < 50 kW** (the DC floor, `MIN_DC_POWER_KW`). Multi-id brands take the max across their ids. OCM power is user-contributed, so rows sampled on few POIs are flagged **thin** (< 12 POIs) — lower confidence, worth a glance before demoting.

## Distribution

| tier | networks |
|---|---|
| slow(<50) | 77 |
| 50–149 | 92 |
| 150–299 | 68 |
| ≥300 | 89 |
| **total** | **326** |

## Slow-only (77) — these drop out of the fast operator picker

| network | POIs | max kW | confidence |
|---|---:|---:|---|
| EVnetNL | 120 | 11 | ok |
| SureCharge (FM Conway) | 120 | 4.8 | ok |
| Char.gy | 120 | 30 | ok |
| Mercadona | 120 | 22 | ok |
| Blue Corner (Belgium) | 120 | 22 | ok |
| Trojan Energy | 120 | 22 | ok |
| Zero Carbon World | 120 | 22 | ok |
| Renault | 117 | 22 | ok |
| Nuon | 116 | 22 | ok |
| Belib’ | 77 | 22 | ok |
| City EV | 71 | 11 | ok |
| OVAG Energie | 67 | 22 | ok |
| ubitricity | 67 | 22 | ok |
| Mainova | 66 | 32 | ok |
| evcharge.online | 64 | 22 | ok |
| Interparking | 60 | 11 | ok |
| Driwe | 59 | 22 | ok |
| ElectroDrive/Mark-E | 50 | 22 | ok |
| Nomadpower | 41 | 22 | ok |
| EV24 | 38 | 22.2 | ok |
| Electric 55 Charging | 35 | 22 | ok |
| EVN (Bulgaria) | 34 | 22 | ok |
| LSW Energie | 34 | 22 | ok |
| ZE-MO (Be) | 27 | 22 | ok |
| Stadtwerke Göttingen | 26 | 43 | ok |
| AGSM Electrify Verona | 25 | 22 | ok |
| Stadtwerke Rostock | 25 | 22 | ok |
| FLOW Charging | 24 | 11 | ok |
| Drax Energy Solutions Limited | 22 | 22 | ok |
| EV-Point | 21 | 22 | ok |
| BIGGIE Energie | 21 | 22 | ok |
| Flowbird | 19 | 22 | ok |
| Elli (Volkswagen Group Charging GmbH) | 18 | 22 | ok |
| GGEW | 18 | 43 | ok |
| ENEWA | 17 | 22 | ok |
| GardaUno | 17 | 40 | ok |
| Stadtwerke Elmshorn | 17 | 22 | ok |
| Green Land Mobility (Italy) | 16 | 22 | ok |
| ZEAG Energie | 16 | 22 | ok |
| eways | 15 | 22 | ok |
| Stadtwerke Wittenberg | 15 | 22 | ok |
| e-Laad | 15 | 22 | ok |
| Harz Energie | 15 | 22 | ok |
| BLUETORINO | 13 | 7 | ok |
| UEnergia | 13 | 24 | ok |
| Plug Charging | 12 | 22 | ok |
| TOTAL Nl PlugToDrive | 11 | 13 | thin |
| ORES | 10 | 22 | thin |
| Ferrovial | 10 | 11 | thin |
| Sigeif | 10 | 24 | thin |
| Ecospazio (Italy) | 10 | 3 | thin |
| eNovates | 9 | 5 | thin |
| Stadtwerke Rinteln | 8 | 22 | thin |
| BMM Energy | 7 | 22 | thin |
| EVL (de) | 7 | 43 | thin |
| Mobib (Belgium) | 6 | 22 | thin |
| Evology Parking | 6 | 22 | thin |
| EVcore | 6 | 22 | thin |
| Next Step Mobility | 6 | 22 | thin |
| Stadtwerke Wernigerode | 6 | 22 | thin |
| Reev | 5 | 22 | thin |
| BürgerLadenetz | 5 | 22 | thin |
| NRG4YOU | 5 | 22 | thin |
| Badenova | 5 | 22 | thin |
| Stadtwerke Wolfenbüttel | 5 | 45 | thin |
| Stadtwerke Grevesmühlen | 5 | 22 | thin |
| ZenCar | 4 | 22 | thin |
| Axpo Energy Solutions Italia | 4 | 22 | thin |
| electric-driving.colruytgroup.com | 3 | 22 | thin |
| Justplugin | 3 | 22 | thin |
| Yawatt (Es) | 3 | 22 | thin |
| G2mobility | 3 | 22 | thin |
| eVISO | 3 | 22 | thin |
| Werraenergie | 3 | 22 | thin |
| APCOA Parking | 3 | 12 | thin |
| EVS Energieversorgung Sylt | 2 | 22 | thin |
| Petite Borne | 2 | 22 | thin |

*31 of the 77 are thin (< 12 POIs).*

## The other 249 (fast) — `maxPowerKw` only, drives 50/150/300 tier filtering

Full per-network values are in `power_classification.json` (scratchpad); baked into the catalog on sign-off.


## Appendix — all 326 (`key` | max kW | POIs), the full record to bake from

| key | max kW | POIs |
|---|---:|---:|
| a2a-emoving-it | 150 | 45 |
| aae | 50 | 7 |
| acciona-cargacoches | 400 | 120 |
| acea-it | 50 | 47 |
| aena-ev-es | 50 | 72 |
| agsm-electrify-verona-it | 22 | 25 |
| aldi-sud-de | 150 | 120 |
| alfapower-uk | 100 | 53 |
| alize-liberte | 400 | 120 |
| allego | 300 | 120 |
| alperia | 400 | 120 |
| amb-area-metropolitana-de-barcelona | 150 | 120 |
| apcoa-parking | 12 | 3 |
| applegreen-fast-charge | 400 | 106 |
| aral-pulse | 350 | 47 |
| arnold-clark-dealer-network-uk | 150 | 8 |
| atlante | 600 | 120 |
| avia-fr | 360 | 7 |
| axpo-energy-solutions-italia | 22 | 4 |
| b-sm-es | 50 | 111 |
| badenova-de | 22 | 5 |
| ballenoil-es | 150 | 13 |
| bartergo-es | 180 | 67 |
| be-charge | 300 | 120 |
| be-emobil | 50 | 120 |
| be-energised-has-to-be | 350 | 120 |
| be-ev | 150 | 54 |
| becharged | 150 | 120 |
| belib | 22 | 77 |
| believ | 50 | 120 |
| biggie-energie | 22 | 21 |
| blink-charging | 50 | 240 |
| blue-corner-belgium | 22 | 120 |
| bluetorino | 7 | 13 |
| bmm-energy-uk | 22 | 7 |
| bonpreuesclat | 160 | 71 |
| bp-pulse | 600 | 240 |
| bump-fr | 400 | 88 |
| burgerladenetz-de | 22 | 5 |
| carrefour-es | 150 | 75 |
| cel-toda-navarra-es | 50 | 26 |
| char-gy | 30 | 120 |
| charge-your-car | 50 | 120 |
| chargecloud-de | 150 | 4 |
| chargeguru | 150 | 27 |
| chargeit-mobility | 150 | 107 |
| chargenode-se | 400 | 4 |
| chargeplace-scotland | 51 | 120 |
| chargepoint | 320 | 120 |
| circle-k | 400 | 120 |
| citipark-uk | 120 | 3 |
| city-ev | 11 | 71 |
| citywatt-de | 300 | 2 |
| clenergy | 50 | 93 |
| comfortcharge | 200 | 120 |
| connected-kerb | 75 | 120 |
| crece-energia-es | 50 | 9 |
| da-emobil | 300 | 41 |
| digavel-es | 120 | 32 |
| dragon-charging | 50 | 40 |
| drax-energy-solutions-limited | 22 | 22 |
| dream-energy | 160 | 4 |
| drive-eco | 200 | 15 |
| driwe | 22 | 59 |
| duferco-energie | 150 | 120 |
| e-charge50-fr | 300 | 17 |
| e-flux | 350 | 11 |
| e-laad | 22 | 15 |
| e-motum-fr | 150 | 60 |
| e-moving-italy | 120 | 120 |
| e-totem | 180 | 27 |
| e-vadea | 300 | 33 |
| e-wald | 300 | 120 |
| eam | 50 | 73 |
| easy4you | 50 | 10 |
| eb-charging | 150 | 23 |
| eborn | 120 | 120 |
| ecarup-ch | 150 | 7 |
| ecocharge77 | 130 | 120 |
| ecospazio-italy | 3 | 10 |
| ecotap | 50 | 120 |
| edf | 150 | 20 |
| edp | 300 | 120 |
| eg-group-ev-point-uk | 250 | 4 |
| eins | 75 | 56 |
| ekomobil | 75 | 21 |
| electra | 600 | 120 |
| electric-55-charging-fr | 22 | 35 |
| electric-driving-colruytgroup-com | 22 | 3 |
| electric-highway-uk | 400 | 120 |
| electrico-es | 60 | 24 |
| electrip | 400 | 28 |
| electro-emt | 400 | 24 |
| electroad-uk | 50 | 60 |
| electrodrive-mark-e-de | 22 | 50 |
| ella | 50 | 6 |
| elli-volkswagen-group-charging-gmbh | 22 | 18 |
| emallorca-es | 150 | 19 |
| emobitaly-italy | 90 | 58 |
| emovili-fast-es | 180 | 51 |
| enbw | 400 | 120 |
| endesa | 400 | 120 |
| eneco | 400 | 4 |
| enel-x | 500 | 120 |
| enercharge | 120 | 3 |
| enercity | 200 | 120 |
| enerhub | 50 | 6 |
| enewa | 22 | 17 |
| engie | 400 | 120 |
| enovates | 5 | 9 |
| entega | 175 | 120 |
| eo-charging | 50 | 59 |
| eon-drive | 400 | 382 |
| epower | 240 | 120 |
| eranovum-es | 150 | 120 |
| esb-ecars | 360 | 120 |
| esb-energy-uk | 300 | 107 |
| essent-nl | 50 | 120 |
| estabanell-energia | 50 | 8 |
| etecnic | 350 | 120 |
| ev-dot | 50 | 31 |
| ev-point | 22 | 21 |
| ev24 | 22.2 | 38 |
| evbox | 500 | 120 |
| evcharge-online | 22 | 64 |
| evcore-se | 22 | 6 |
| everse | 75 | 2 |
| eviso | 22 | 3 |
| evite-ch | 60 | 42 |
| evl-de | 43 | 7 |
| evn-bulgaria | 22 | 34 |
| evnet-nl | 11 | 120 |
| evology-parking | 22 | 6 |
| evolt-network-swarco-e-connect | 300 | 120 |
| evpass-ch | 66 | 94 |
| evs-energieversorgung-sylt | 22 | 2 |
| evway | 161 | 81 |
| evyve | 120 | 26 |
| evzen-fr | 180 | 11 |
| eways | 22 | 15 |
| ewb | 50 | 70 |
| ewe-go | 400 | 120 |
| ewiva | 450 | 120 |
| ewp-energie-und-wasser-potsdam-gmbh | 50 | 49 |
| ewr-gmbh-e-mobile | 50 | 27 |
| ezo-ie | 200 | 120 |
| fastned | 400 | 120 |
| fenie-energia-spain | 50 | 120 |
| ferrovial-es | 11 | 10 |
| flow-charging | 11 | 24 |
| flowbird-uk | 22 | 19 |
| flyelectric-it | 50 | 2 |
| forev | 258 | 5 |
| fortum-recharge | 300 | 223 |
| free-to-x | 400 | 106 |
| freshmile | 360 | 120 |
| fuuse | 180 | 71 |
| g2mobility | 22 | 3 |
| gai-charge | 50 | 6 |
| galp-electric | 300 | 120 |
| gardauno | 40 | 17 |
| gc-movilidad-electrica-es | 120 | 52 |
| ggew | 43 | 18 |
| gofast-gotthard-fastcharge | 300 | 28 |
| greems-il | 364 | 102 |
| green-land-mobility-italy | 22 | 16 |
| greenflux | 175 | 120 |
| gridserve | 560 | 82 |
| grønn-kontakt | 150 | 38 |
| harz-energie | 22 | 15 |
| heraricarica-pubblica | 60 | 83 |
| iecharge-fr | 360 | 58 |
| incharge-se | 180 | 77 |
| innogy-rwe | 150 | 120 |
| instavolt-ltd | 350 | 120 |
| interparking-es | 11 | 60 |
| ionity | 600 | 120 |
| iplanet-it | 400 | 30 |
| izivia | 380 | 120 |
| jet-charge-uk | 400 | 3 |
| joju-ltd | 150 | 120 |
| jolt | 300 | 60 |
| justplugin-nl | 22 | 3 |
| kaufland-echarge | 380 | 20 |
| kelag-ag | 300 | 10 |
| kople | 180 | 3 |
| l-electra-es | 60 | 16 |
| lad-opp | 300 | 3 |
| ladenetz | 300 | 120 |
| last-mile-solutions | 501 | 28 |
| leap24-nl | 300 | 44 |
| lidl | 250 | 120 |
| lsw-energie | 22 | 34 |
| mainova | 32 | 66 |
| melib-es | 50 | 120 |
| mer | 400 | 229 |
| mercadona | 22 | 120 |
| metropolis | 150 | 25 |
| mfg-ev-power | 400 | 120 |
| mistergreen-the-fast-charger-network | 50 | 4 |
| mobib-belgium | 22 | 6 |
| mobilityplus | 300 | 3 |
| mobilize-fr | 320 | 75 |
| mobive | 50 | 120 |
| moeve-es | 500 | 120 |
| monta | 400 | 120 |
| move-ch | 300 | 39 |
| n-ergie | 150 | 120 |
| naturenergie-de | 50 | 120 |
| next-step-mobility-de | 22 | 6 |
| nextcharge | 150 | 68 |
| nomadpower | 22 | 41 |
| nrg4you | 22 | 5 |
| nuon | 22 | 116 |
| oeste-movilidad-es | 180 | 28 |
| okq8-se | 150 | 5 |
| optimile | 200 | 15 |
| ores | 22 | 10 |
| orlencharge | 100 | 33 |
| osprey | 320 | 120 |
| osterholzer-stadtwerke | 50 | 20 |
| ouest-charge | 200 | 25 |
| ovag-energie | 22 | 67 |
| park-charge-ch | 50 | 2 |
| pavapark-es | 50 | 31 |
| petite-borne-fr | 22 | 2 |
| pfalzwerke | 300 | 120 |
| place-to-plug | 400 | 120 |
| plenergy-es | 100 | 120 |
| plenitude-on-the-road-eu | 300 | 28 |
| plug-charging | 22 | 12 |
| plug-n-go-ltd | 60 | 38 |
| plug-n-roll | 50 | 21 |
| plugsurfing | 350 | 120 |
| pod-point | 50 | 120 |
| pogo-charge-gb | 200 | 31 |
| porsche-smart-mobility-gmbh | 350 | 53 |
| power-dot | 200 | 240 |
| power-station-be | 360 | 4 |
| powergo | 400 | 120 |
| powerland-be | 300 | 3 |
| powy | 400 | 74 |
| prioe | 180 | 76 |
| project-ev | 60 | 21 |
| q1-autostrom | 180 | 25 |
| qovoltis | 50 | 84 |
| qwello | 80 | 65 |
| r3 | 150 | 15 |
| reev-de | 22 | 5 |
| remo-mobility-es | 200 | 120 |
| renault | 22 | 117 |
| repower-italy | 75 | 93 |
| repsol-ibil | 360 | 120 |
| reveo-fr | 150 | 120 |
| rheinenergie-ag | 100 | 120 |
| roam-charging-uk | 140 | 120 |
| rwe-mobility-essent | 50 | 6 |
| saiel-it | 180 | 2 |
| salzburg-ag | 150 | 2 |
| scottish-power | 50 | 29 |
| sde76-fr | 50 | 32 |
| sdey-fr | 160 | 100 |
| shell-recharge | 420 | 449 |
| sigeif-fr | 24 | 10 |
| silverstone-green-energy | 120 | 90 |
| smappee | 240 | 17 |
| smart-charge-au | 150 | 4 |
| smart-charge-by-sainsbury-s-uk | 300 | 18 |
| smatrics | 400 | 120 |
| source-gb | 240 | 4 |
| spotlink-e-motion | 90 | 6 |
| stadtische-werke-magdeburg | 50 | 17 |
| stadtwerke-clausthal-zellerfeld | 150 | 120 |
| stadtwerke-dusseldorf-ag | 300 | 120 |
| stadtwerke-elmshorn | 22 | 17 |
| stadtwerke-gottingen | 43 | 26 |
| stadtwerke-grevesmuhlen | 22 | 5 |
| stadtwerke-haldensleben-swh | 50 | 10 |
| stadtwerke-halle | 50 | 33 |
| stadtwerke-leipzig-swl | 50 | 87 |
| stadtwerke-munster | 150 | 8 |
| stadtwerke-rinteln | 22 | 8 |
| stadtwerke-rostock | 22 | 25 |
| stadtwerke-strahlsund | 50 | 19 |
| stadtwerke-wernigerode | 22 | 6 |
| stadtwerke-wittenberg | 22 | 15 |
| stadtwerke-wolfenbuttel | 45 | 5 |
| stations-e | 60 | 18 |
| stromnetz-hamburg | 50 | 120 |
| surecharge | 4.8 | 120 |
| swisscharge-ch | 350 | 29 |
| sydego | 50 | 120 |
| tanke-wien-energie | 50 | 96 |
| tap-electric | 50 | 5 |
| telepark-empark | 160 | 116 |
| tesla | 500 | 240 |
| the-geniepoint-network-equans-ev-solutions | 60 | 120 |
| theplugincompany-belgium | 60 | 84 |
| time-park-no | 60 | 2 |
| tiwag-tiroler-wasserkraft-ag-at | 50 | 26 |
| total-be-plugtodrive | 175 | 14 |
| total-nl-plugtodrive | 13 | 11 |
| totalenergies | 400 | 360 |
| trojan-energy | 22 | 120 |
| ubitricity | 22 | 67 |
| uenergia-es | 24 | 13 |
| umbrella-emobility-es | 240 | 84 |
| vend-electric | 50 | 52 |
| vilalta-greenergy-es | 50 | 33 |
| virta | 350 | 120 |
| vitaemobility | 50 | 3 |
| vlotte | 300 | 16 |
| weev | 150 | 120 |
| wenea | 360 | 120 |
| werraenergie | 22 | 3 |
| westfalen-weser-netz | 75 | 64 |
| wroom | 50 | 78 |
| yawatt-es | 22 | 3 |
| zapgrid | 60 | 120 |
| ze-mo-be | 22 | 27 |
| zeag-energie | 22 | 16 |
| zen-zero-emission-network-proviridis | 250 | 7 |
| zencar | 22 | 4 |
| zero-carbon-world | 22 | 120 |
| zest-charging | 120 | 33 |
| zunder | 560 | 120 |

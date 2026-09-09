# Charging network filter
- ~~The search field needs an (x) button at the end to clear the input~~ — done
- ~~Search is very slow because it runs after every keystroke. It needs a debounce.~~ — done
- ~~Displaying the networks is slow because there are so many entries. This probably needs a loading indicator, and the filtering must not run on the UI thread.~~ — done
- ~~When no search term is entered, the selected networks should be shown at the top of the result list.~~ — done

# Planning
- When searching for destinations, the result list should appear in an overlay (AutoComplete text field).
- If you pick a charging station and then "Show in Maps", Maps plans a route instead of jumping to the location
- The "Select section" function should always send "My location" as the first point, otherwise Google Maps cannot navigate
- The "Select section" function should mark all stations that will be sent, not just the first and the last
- When a plan is shown or loaded from favorites, there shall be a Button to re-plan, which goes back to the "Plan route" Bottom sheet but has the destination pre-filled.
- When a plan is shown or loaded from favorites, there should be a quick option to input the current SOC at start

# Map
- The map view should give charging stations an icon depending on charging speed. A bolt, red = slow (max 22 kW), yellow = min 50 kW, green = min 100 kW, two green bolts 150 kW and so on.
- In addition, the operator should be shown in short form. For now only for the best-known operators: EWE Go, EnBW, Tesla, Aral, Shell, e.on, Ionity.
- When clicking a charging station, the title shows the place name. The title should show the operator instead.
- The price should no longer be displayed directly on the map.

# Favorites
- The favorite icon on the main screen should be filled
- In the favorites overview you should be able to swipe to delete an entry; the cross can go away
- Under "Recent" the heart should not be filled — depending on whether that entry has already been favorited

# Charge now
- The "Charge now" function should additionally show the charging stations on the map

# Car
- It should be possible to add a new car that is not yet in the list
- ~~The MG4 Urban 54 kWh should be added~~ — done
- The consumption slider should get a reset button
- Car data should be filled in from a data source like gaia-charge/evdb (respect attribution). Can be done statically through some update script. Move to backend later.

# Drawer
- The minimum power should also offer 11 kW as an option

# UI
- The sliders have a very large vertical thumb; it can be smaller
- The buttons for "Send to Maps" should be consistent. In planning it is a flag, elsewhere an arrow. It should always be a flag.
- Clicking a charging station on the map should show the station's details in a bottom sheet
- A planned route should show the projected SOC at each stop after the time of arrival

# Misc
- A license screen is required, reachable from the drawer. Use some gradle license plugin to generate license information from dependencies.
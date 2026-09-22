# From plans to committed trips

Date: 2026-09-22. Direction agreed in conversation, not yet a spec.

## What the app is for

ChargeAhead makes Google Maps behave more like a Tesla's trip planner: it
adds the charging logic Maps lacks — which stop, how long, will I make it —
and leaves navigation to Maps. It is not a Komoot: "plan a route once and
drive it again later" is not the product.

Consequence: saving routes is not a core feature. Recent destinations in
the search cover "the drive I did last week". The Favoriten pill, the routes
sheet and the heart in the trip sheet go once the committed trip exists.

## The loop

1. **Search and commit.** The driver searches a destination in our app and
   commits to the trip. From then on the phone (and later the car) shows the
   chargers on the way as *the trip you are on*, not as a plan to send.
2. **Watch.** Position along the route and the vehicle's charge, from Android
   Auto when connected, otherwise the manual value, which the app asks for at
   commit and after each stop.
3. **Judge.** Project the position onto the route, mark passed stops done,
   predict the charge on arrival at the next stop with the consumption model.
4. **Re-plan on a real event only.** Predicted arrival charge under the
   margin, a planned stop skipped, or leaving the route corridor. Debounced,
   so one bad fix triggers nothing.
5. **Prompt once.** Plan again from here with the current charge. If the
   stops changed: one prompt, "An Maps senden" as the action, on the phone
   and in the car session when connected. Never repeat a dismissed prompt for
   the same stop. Quiet otherwise: automatic re-plans plus popups while
   driving is a distraction pattern.
6. **End.** The trip ends at the destination or when the driver clears it;
   nothing keeps running afterwards.

## What exists, what is new

Exists: the trip planner and consumption model (`shared/core`), `TripStore`
shared by phone and car, the trip sheet with the Maps hand-off, the car
surface as a POI app with list and place templates.

New:

- A **committed trip** that survives restarts and drives both surfaces.
  `TripStore` holds "the last plan" today.
- **Progress along the route**: which stops are behind, distance to the next.
- **Reachability of the next stop**: consumption against current charge with
  a margin. Mostly planner code, reused.
- **Re-plan events and the prompt.**
- **A background host** for the loop while Maps is in the foreground (below).

## The two bottlenecks, and why they don't block this

**Android Auto categories.** Besides media, messaging and navigation the Car
App Library has POI, which this app already is
(`androidx.car.app.category.POI`, ABRP and PlugShare ship the same way).
POI apps may show lists and places, hand off to the default navigation app
and run while the head unit session is active. They may not draw a route on
a map or do turn-by-turn. The car surface therefore stays "next stop, your
charge, tap to re-route in Maps". Open: whether a POI app may show a
heads-up prompt while Maps is on the head unit screen. Check the current Car
App Library docs before the spec; the fallback is the phone notification and
the car showing the prompt when the driver switches back.

**Play and Google's routing.** Play does not reject apps that resemble Maps;
a charging planner that hands navigation off to Maps is a distinct product.
The Maps Platform routing terms forbid building a competing navigation
product on their data and showing their routes on a non-Google map; planning
stops and sending the driver to Google Maps is the sanctioned pattern. Whether
those terms apply at all depends on the engine behind `BackendRouteEngine`.

## The background loop

Without a foreground service a background app gets a few location updates
per hour and can be killed. The sanctioned way is a foreground service with
`foregroundServiceType="location"`, the `FOREGROUND_SERVICE_LOCATION`
permission, a persistent notification, and a use-case declaration in the
Play Console. The notification should be honest and useful (next stop,
charge margin) and the service must stop when the trip ends.

While Android Auto is connected, the `CarAppService` keeps the process alive
for the session; the loop can run there without a phone-side service. One
host owns the loop at a time, the other only displays.

Costs: battery (high-accuracy fixes every few seconds while moving, back off
when stationary), OEM background killers (the car session is the more
reliable host), a manual charge value that goes stale on the phone alone.

Testing: none of this runs on Robolectric. The decision logic lives in
`shared` with unit tests; the service and the car session are thin shells.

## Order of work

1. Decision logic in `shared`, unit-tested: progress along the route,
   next-stop reachability, re-plan events.
2. Committed trip state and the prompt on the phone, foreground only.
3. The car session as loop host — no Play declaration needed, and the car
   is the real target.
4. The phone foreground service, once the logic has proven itself in the car.
5. Remove saved routes: Favoriten pill, routes sheet, heart, `SavedRoute`
   storage.

Prerequisite: PR #126 (`ui-tests`) merged, so the work builds on one branch
and the shell flow tests cover the commit and prompt flows from the start.

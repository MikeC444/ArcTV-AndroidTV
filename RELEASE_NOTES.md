# Release notes

User-facing changes to Arc TV, newest first. This file *is* the release
notes shown in the app's own update banner (tap the "i" info button) --
whatever sits under a version heading here becomes that release's
description, so there's no need to visit GitHub to see what changed.

(For the backend/engineering build log, see CHANGELOG.md instead -- this
file is only ever end-user-facing release notes.)

When cutting a new release: rename `## Unreleased` below to the new
version (e.g. `## 0.1.2`), add a fresh empty `## Unreleased` above it, then
run the release workflow for that version.

## Unreleased

- The update pop-up now shows the notes for every release you missed, newest first, all in the pop-up itself (scroll them with the remote). It never sends you to GitHub, and a release with no notes says "Bug fixes and improvements."
- "Picked for you" (preview): a Home row chosen from the movies you've liked, finished and saved, matched on genre, director and cast, with a short reason under each poster ("Because you liked ..."). Like and Not for me are in a poster's long-press menu and on a movie's page, they sync across your devices, and a Not for me title leaves the row at once. Until you have a few movies to learn from, the row says plainly that it is just popular movies. Only in test builds for now.
- New Settings > Blocked Genres: switch off any genre and its titles disappear from Home, Movies, TV Shows, Search, Genres and "More like this". The list is saved to your account, so it follows you to every device you sign in on. Titles an addon gives no genres for can't be filtered.
- Sources whose audio the device can't decode (it stopped with "Unable to play this source" and an audio error) now switch to the next audio track in the file and carry on from the same place, and also try the device's other audio decoder first. Playback errors also show an error code to help track down any that remain.
- The app starts faster, especially the first time you open it after installing or updating: Home's rows now load in fewer, bigger steps with the first row on its own so something shows sooner, and the app prepares its own code ahead of time instead of working it out while you wait.
- The featured titles on Home now slide in from the right (picture and text together) instead of fading, small dots at the bottom right show which of them you're on, and the picture's left and right edges have the same soft shade as the top bar
- TV show pages are tighter: the title, details and Play button sit lower on a shorter picture so the seasons and episodes start right below instead of a screenful down, and the rating sits level with the Play button
- You can now look around without an account: the app opens straight to Home, and you can browse Movies, TV Shows, Genres, Search and any title's page. The first time you press Play, open My List or Settings, or try to save a title or mark it watched, it asks you to sign in, and takes you back to what you were doing afterwards. The Settings tab reads "Sign In" until you're signed in, and signing out now offers "Browse without an account"
- On a movie or show's page the Trailer button is always there from the start, dimmed until a trailer is found, instead of appearing late and shifting the other buttons
- Select a Source: the poster is bigger, and the list now starts sorted by file size (biggest first), with the Recommended source still always at the top
- The big featured title at the top of Home has a Trailer button next to Play (dimmed until a trailer is found). Its pictures are also sharper on a big TV, and the next ones are fetched ahead of time so each slide appears straight away instead of loading in
- The "update available" message is now a pop-up in the same style as "What's new", instead of a bar across the top of the screen. It shows the new version and its release notes right in it, with Update and Not now buttons, and shows download progress there too. The release notes can now be scrolled with the remote: press up to select them, then up and down to read all of it
- The startup video is gone: the app now opens straight to the sign-in or Home screen instead of playing an intro first
- Select a Source now tells you whether a Real-Debrid (or other debrid) source is ready: "Cached on Real-Debrid" starts straight away, while "Not cached on Real-Debrid — may take minutes" means the service still has to fetch the file first. Sources that start at once are now listed and recommended ahead of ones you'd have to wait for
- When Select a Source has nothing to show, it now says why: no addons installed, none of your addons provide streams, an addon didn't answer (with a Try Again button), or they all answered but have nothing for this title. Each addon's answer is listed, and if some addons failed while others found sources, a note says the list may be incomplete
- New Arc TV logo, launcher icon and Fire TV home-screen banner, and the logo in the top bar, sign-in screens and the player now use the full Arc TV artwork instead of the old "MANGO TV" text
- Mango TV is now Arc TV: the app's name, and every place that used to say Mango TV (sign-in, Settings, the update prompt, the add-addon page on your phone), now say Arc TV
- New look: the app's colours now follow the Arc TV logo (cyan, blue and violet) instead of amber and orange, across buttons, selected items, switches, the focus highlight and the progress bars. Warnings stay amber and errors stay red
- Cast on a movie or show's page now shows each actor's photo and the character they play, instead of a blank circle with just a name, and TV shows now list their cast under the episodes
- Pressing BACK from a movie or show's page now returns you to the exact poster you left on Home, in the same row and at the same scroll position, instead of jumping back to the top
- My List has a "Sort by" drop-down beside its title: Recently Added (the default), A–Z, Highest Rated and Newest
- Movies and TV Shows have an "All genres" drop-down beside the title, so you can browse just one genre (Action, Comedy, Sci-Fi and so on)
- Each movie or show now appears in only one Home row (the first one it belongs to), instead of repeating down the page, and a title already on Home isn't repeated under Continue Watching
- On Select a Source, the Recommended source is always the first row, whatever filter or sort you pick
- Movies you removed from My List or took off Watched no longer come back after you sign out and in again, or sign in on another device

## 0.1.3

- My List is now a scrollable multi-column catalogue like Movies/TV Shows/Genres, instead of a single horizontal row -- the All/Watched filter still works the same way, and the newest titles you've added show up first
- Movie and TV show detail pages' three-dot menu, and long-pressing a poster anywhere in the app, both now have a working "Mark as watched" option, which switches to a filled checkmark once used -- tapping it again removes the watched status
- Movies you finish watching (past ~85%) now get a green checkmark on their poster everywhere it appears (Home, Movies, TV Shows, Genres, Search, and Detail), and are automatically added to My List under a new "Watched" filter -- My List's default view still mixes watched titles in with everything you added yourself. This also runs once against your existing watch history, so movies you'd already finished before this update get picked up too, not just ones you finish from now on
- Trailers now open in the app of your choice (the YouTube app, a browser, whatever you have installed) instead of playing inside MangoTV, which fixes trailers playing sound over a black screen
- Movie detail pages now show the real release date when it's available, instead of just the year
- Settings is now a two-pane layout: pick a category (Account, Addons, Home Rows, Sounds, Subtitles) on the left, its settings show on the right
- Settings tabs are more compact and each one now scrolls as a whole, so long lists like Addons or Subtitle languages show more per screen
- Settings' focus highlight is now white instead of amber
- Settings > Home Rows loads faster with multiple addons installed
- Select a Source now shows results as soon as each addon responds, instead of waiting for the slowest one before showing anything
- The Home page hero now rotates through 10 random movies/TV shows from your whole catalog, picked fresh each time you launch the app, instead of always the same first row's top 10
- Fixed needing to press BACK twice to hide the player controls while the timeline was selected
- Update banner's Update button is now white instead of amber
- Fixed the Close/Open Settings buttons on the update overlays being unselectable
- Allow-installing-updates overlay's Open Settings button is now white instead of orange

## 0.1.1

- Redesigned the Genres tab: genres are now a grid of colorful cards with
  an icon per genre, instead of a plain list

## 0.1.0

- First public release
- Added in-app updates: the app now checks for new versions itself and
  lets you download and install them from a banner, without needing to
  re-sideload from scratch
- Various stability and UI fixes

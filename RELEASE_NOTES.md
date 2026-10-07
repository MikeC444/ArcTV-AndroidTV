# Release notes

User-facing changes to Arc TV, newest first. This file *is* the release
notes shown in the app's own update banner (tap the "i" info button) --
whatever sits under a version heading here becomes that release's
description, so there's no need to visit GitHub to see what changed.

(For the backend/engineering build log, see CHANGELOG.md instead -- this
file is only ever end-user-facing release notes.)

Releasing needs nothing done to this file: add each change as a bullet under `## Unreleased` as you make it. When a release is
published, the workflow uses what is under `## Unreleased` as that release's notes (what the app's update pop-up shows), then moves it
under the new version heading and leaves a fresh empty `## Unreleased` above it.

## Unreleased

## 0.3.0

- Surround sound now reaches all speakers in the VLC player, and the Speakers setting only filters sources.
- Support for torrent addons, with buffer and storage settings. Nothing extra to install.
- No sources yet? Scan a QR code for a step-by-step setup guide.
- Settings has a new Updates tab with your app version and a Check for updates button.
- New to Plus? Your first monthly or yearly subscription now starts with 5 free days.
- Plus: Smart source picking plays the best source for you, skipping the list. Switch it in the new Plus settings tab.
- Plus: Your stats shows your watch time, streaks and busiest days.
- Fixed a crash in Settings when moving back into a tab you had scrolled.
- A clean black Arc TV logo screen at startup while the app loads.

## 0.2.0

- A new VLC-powered player is now the default: streaming-style controls, and it plays more videos.

- No sound on surround sources? Turn off Audio Passthrough in Settings > Audio, or instantly in the player.

- Can't play a source? Pick the built-in player, VLC or another app with the new button by the timeline.

- Next episode stays on screen through the last minute, with the cursor on it. Press OK to play.

- New Episodes panel in the player: pick any season and episode with its thumbnail, and it plays right away.

- Player menus no longer let the cursor slip behind them, so Subtitles, Episodes and the rest stay put.

- Dolby Vision file won't play? It now retries by itself as HDR10.

- Choose your default player in Settings > Player, and each title remembers the player you picked for it.

- Cleaner source filters: the Audio pill is no longer cut off, and the dark halo around pills is gone.

- Pick your preferred audio language in Settings > Audio, and it follows you to all your devices.

- Pick stereo, 5.1, 7.1 or Atmos in Settings > Audio and the source list matches it.

- Sources with DTS, DTS-HD or TrueHD sound now play with audio, even on TVs that can't decode them.

- The update pop-up is bigger and easier to read, and Home just dims behind it.

## 0.1.8

- Press a Continue Watching title and it carries straight on, using the same source you watched before, with no source list.

- Scrubbing is easier: hold Left or Right on the timeline to glide through the video in steady 10-second steps, with no limit.
- The player remembers which button you were on, so closing a menu or the controls coming back no longer jumps to Play.
- Editing a profile now fits the screen and scrolls, with Save and Cancel always in view, and the keyboard only opens when you press OK on the name.
- Opening a title now shows its artwork and logo with a loading symbol, instead of a blank screen.
- A Next episode button appears in the last minute, and Auto Play Next Episode now really plays the next one.
- Your playback speed is remembered, and the right-hand time shows time left; press it for the total.
- Settings in the player always has an Audio row, and it tells you when a source has only one track.
- Continue Watching now remembers any amount you watch, and always shows on Home.
- Cancel a monthly or yearly Arc TV Plus subscription right from Settings > Arc TV Plus.
- Sixteen new illustrated profile pictures to choose from.
- Recent searches are now kept separately for each profile.
- Settings > Addons now explains that only addons with a debrid service will play.
- The Trailer button now has the same rounded shape as Play.

## 0.1.7

- Search just got faster and friendlier: results appear as you type, with recent searches and a roomy poster grid.
- A fresh top bar: your links float in a sleek pill, and the Home picture now stretches edge to edge.
- Settings has a tidy side menu, grouped into You, Content, and Playback & sound, and uses the whole screen.
- The Genres tab is gone: pick a genre from the menu on Movies and TV Shows instead.
- Fixed: pressing Up in Movies and TV Shows now stops at Featured and the genre menu on the way back to the top bar.
- Fixed: the blue outline around the Trailer button no longer looks cut off.
- Smoother Home: the next picture is ready before it glides in, and the slide is gentler.
- The update pop-up is now a small, tidy card instead of filling the screen.

## 0.1.6

- Your "Picked for you" row just got smarter: it matches your whole mix of tastes, and most of it changes every time you open the app.
- Movies you've liked never show up in "Picked for you" again.
- Not feeling a pick? Press and hold it and choose "Remove from Picked for you".
- Switch profile in one press: your picture and name now sit at the top right of the top bar.
- Profiles not showing? Settings > Account now tells you why.
- Fixed: after you sign in, your My List, Continue Watching and addons now load right away.

## 0.1.5

- Profiles are here for Arc TV Plus: each person on your account can have their own profile (up to 5, any mix of adult and kids) with their own My List, Continue Watching, settings, addons and Likes. If your account has more than one profile, the app opens on "Who's watching?" each time you start it; pick one with the remote. Add, rename, change the picture of, or remove profiles under "Manage profiles", and lock any profile with a 4-digit PIN (asked when you open, change or remove it; five wrong tries in a row pause guessing for five minutes). Your name sits at the end of the top bar to switch profiles, and Settings > Account says who is watching. A kids profile has no Settings and never shows horror, thriller, crime, war, mystery or other mature-genre titles (titles an addon gives no genres for can't be filtered, as with Blocked Genres). Everything you already had stays on your main profile.

## 0.1.4

- The Home hero appears much sooner when you open the app: each time you use the app it quietly picks and downloads the titles for next time's hero, so on the next launch the first slide shows from storage straight away (and no longer changes a few seconds in when the live rows arrive). The rest of the slides load a moment later so they don't slow down the first one. The titles still change every launch.
- Settings > Arc TV Plus: the "Payments are handled by Stripe's secure checkout page" note can no longer be selected, and the plan summary at the top no longer shows a white outline when the remote is on it.
- Settings: Arc TV Plus now sits between Account and Addons. On the Arc TV Plus tab, pressing Down on the plan cards no longer jumps sideways between them (it moves down the tab), and the tab no longer shakes slightly while you are on it.
- Settings > Arc TV Plus: you can now scroll back up to the top of the tab (where it shows your plan), and picking a plan goes straight to the payment page with no "Getting your checkout ready" message under the plans.
- Subscribing to Arc TV Plus now has a proper full-screen page: a step indicator, your chosen plan and its price on the left, a large QR code on the right with a countdown while it waits for your payment (no spinner), a "Change plan" button, and a thank-you when it goes through.
- The update pop-up now shows the notes for every release you missed, newest first, all in the pop-up itself (scroll them with the remote). It never sends you to GitHub, and a release with no notes says "Bug fixes and improvements."
- New ArcTV Plus tab in Settings, and ArcTV Plus is in early access: its features are free for everyone for now and will need a Plus subscription once it launches. The first one is "Picked for you": a Home row chosen from the movies you've liked, finished and saved, matched on genre, director and cast, with a short reason under each poster ("Because you liked ..."). Like and Not for me are in a poster's long-press menu and on a movie's page, they sync across your devices, and a Not for me title leaves the row at once. Until you have a few movies to learn from, the row says plainly that it is just popular movies. You need to be signed in. When Plus launches you'll subscribe right in Settings > Arc TV Plus by scanning a QR code with your phone and paying on a secure Stripe page; Plus then switches on by itself.
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

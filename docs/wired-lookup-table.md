# Wired System — Lookup Table & Reference
> **Living document.** Add new entries as each wired box is implemented.
> Last updated: 2026-05-08

---

## What Is Habbo Wired?

Wired is Habbo's room-scripting system. A room owner places invisible furniture boxes that define logic chains:

```
TRIGGER  →  [CONDITIONS]  →  EFFECTS
```

- **Trigger** — what starts the chain (player walks on furni, says something, clicks something, etc.)
- **Conditions** — optional filters; the chain only continues if ALL conditions pass
- **Effects** — what happens (move furni, teleport player, play animation, etc.)

All wired boxes are floor furniture with special interaction classes. They are hidden from normal players and configured via the wired editor UI.

---

## Wired Engine Toggle

The entire new wired engine is gated by a single database setting:

| Setting Key | Table | Required Value |
|---|---|---|
| `wired.engine.enabled` | `emulator_settings` | `1` |

```sql
UPDATE emulator_settings SET value = '1' WHERE key = 'wired.engine.enabled';
```

---

## Trigger Type Codes

| Code | Constant | Class | Status | Notes |
|---|---|---|---|---|
| 0 | `ENTER_ROOM` | WiredTriggerEnterRoom | ✅ Existing | Fires when player enters room |
| 1 | `LEAVE_ROOM` | WiredTriggerLeaveRoom | ✅ Existing | |
| 2 | `FURNI_STATE_CHANGED` | WiredTriggerFurniStateChanged | ✅ Existing | |
| 3 | `GAME_STARTS` | WiredTriggerGameStarts | ✅ Existing | |
| 4 | `GAME_ENDS` | WiredTriggerGameEnds | ✅ Existing | |
| 5 | `WALKS_ON_FURNI` | WiredTriggerHabboWalkOnFurni | ✅ Existing | |
| 6 | `WALKS_OFF_FURNI` | WiredTriggerHabboWalkOffFurni | ✅ Existing | |
| 7 | `SAY_SOMETHING` | WiredTriggerSaySomething | ✅ Existing | |
| 8 | `SAY_SOMETHING_SPECIFIC` | WiredTriggerSaySomethingSpecific | ✅ Existing | |
| 9 | `PERIODIC` | WiredTriggerPeriodically | ✅ Existing | Timed repeat |
| 10 | `PERIODIC_LONG` | WiredTriggerPeriodicallyLong | ✅ Existing | Long timed repeat |
| 11 | `AT_GIVEN_TIME` | WiredTriggerAtGivenTime | ✅ Existing | |
| 12 | `SCORE` | WiredTriggerScore | ✅ Existing | |
| 13 | `TOGGLE_FURNI` | WiredTriggerToggleFurni | ✅ Existing | Double-click |
| 14 | `AVATAR_SAYS_KEYWORD` | — | ❌ TODO | |
| **15** | **`CLICKS_FURNI`** | **WiredTriggerAvatarClicksFurni** | **✅ Implemented (this session)** | **Single-click on floor furni, custom packet 2789** |

---

## Condition Type Codes

| Code | Constant | Notes |
|---|---|---|
| 0 | `FURNI_HAVE_AVATARS` | |
| 1 | `FURNI_HAVE_NO_AVATARS` | |
| 2 | `AVATAR_HAS_EFFECT` | |
| 3 | `AVATAR_WEAR_BADGE` | |
| 4 | `AVATAR_IN_TEAM` | |
| 5 | `NOT_IN_TEAM` | |
| 6 | `ACTOR_IS_ROOM_OWNER` | |
| 7 | `ACTOR_IS_GROUP_MEMBER` | |
| 8 | `ACTOR_IS_HAMC` | |
| 9 | `TEAM_WINS` | |
| 10 | `FURNI_STATE_MATCH` | |
| 11 | `ACTOR_IS_IN_GROUP` | |
| 12 | `NOT_GROUP_MEMBER` | |
| 13 | `FURNI_IS_ON_FURNI` | |
| 14 | `AVATAR_NOT_WEAR_BADGE` | |
| 15 | `NOT_ROOM_OWNER` | |
| 16 | `DATE_RANGE` | |
| 17 | `TIME_RANGE` | |

---

## Effect Type Codes

| Code | Constant | Notes |
|---|---|---|
| 0 | `MOVE_ROTATE` | Move/rotate furniture |
| 1 | `MATCH_TO_SNAPSHOT` | Match furni state to saved snapshot |
| 2 | `TOGGLE_STATE` | Toggle on/off state |
| 3 | `RESET_TIMER` | Reset a periodic timer |
| 4 | `CHAT` | Make avatar say something |
| 5 | `TELEPORT` | Teleport avatar |
| 6 | `GIVE_SCORE` | Give score in a game |
| 7 | `GIVE_SCORE_TEAM` | Give team score |
| 8 | `SHOW_MESSAGE` | Show system message |
| 9 | `KICK_FROM_ROOM` | Kick player from room |
| 10 | `MUTE_TRIGGER` | Mute a trigger temporarily |
| 11 | `ACTIVATE_ENERGY` | |
| 12 | `JOIN_TEAM` | |
| 13 | `LEAVE_TEAM` | |
| 14 | `CHAIN_TO_OTHER` | |
| 15 | `EXECUTE` | |
| 16 | `GIVE_REWARD` | |

---

## Key Source Files — Quick Reference

### Backend (Java)
```
Arcturus-Community/src/main/java/com/eu/habbo/

├── habbohotel/
│   ├── items/interactions/wired/triggers/    ← Trigger box classes live here
│   │   ├── WiredTriggerAvatarClicksFurni.java  (NEW - type 15)
│   │   ├── WiredTriggerHabboWalkOnFurni.java   (reference: type 5)
│   │   └── WiredTriggerFurni.java              (base class)
│   │
│   └── wired/
│       ├── core/
│       │   ├── WiredManager.java               ← triggerXxx() static methods
│       │   └── WiredEvent.java                 ← Event type enum
│       └── migrate/
│           ├── WiredTriggerType.java           ← Type code enum
│           └── WiredEvents.java                ← Event factory methods
│
└── messages/
    ├── incoming/
    │   ├── Incoming.java                       ← Header ID constants
    │   ├── PacketManager.java                  ← Handler registration
    │   └── rooms/items/
    │       ├── ClickFurniEvent.java            (NEW - handles header 2789)
    │       └── ToggleFloorItemEvent.java        ← handles header 99 (double-click)
```

### Frontend (TypeScript)
```
nitro-react/node_modules/@nitrots/nitro-renderer/src/

├── nitro/
│   ├── communication/
│   │   ├── NitroMessages.ts                    ← ALL outgoing composers registered here
│   │   └── messages/
│   │       └── outgoing/
│   │           ├── OutgoingHeader.ts           ← Header ID constants
│   │           └── room/furniture/logic/
│   │               ├── index.ts               ← EXPORT BARREL (must list every composer)
│   │               ├── FurnitureSingleClickComposer.ts  (NEW - header 2789)
│   │               └── FurnitureMultiStateComposer.ts   (reference - header 99)
│   │
│   └── room/
│       └── RoomObjectEventHandler.ts           ← Click/double-click event hooks
```

---

## Packet Headers Used

| Header | Direction | Name | Sender | Handler |
|---|---|---|---|---|
| 99 | Client→Server | ToggleFloorItem / FURNITURE_MULTISTATE | FurnitureMultiStateComposer | ToggleFloorItemEvent.java |
| 2789 | Client→Server | ClickFurni / FURNITURE_CLICK | FurnitureSingleClickComposer | ClickFurniEvent.java |

---

## Implemented Wired Boxes (This Project)

### WiredTrigger — Avatar Clicks Furni (Type 15)
- **Packet:** 2789 (client sends item ID on single-click)
- **Server handler:** `ClickFurniEvent.java`
- **WiredManager method:** `triggerUserClicks(room, roomUnit, item)`
- **WiredEvent type:** `USER_CLICKS`
- **Also fires on:** Double-click via `ToggleFloorItemEvent.java` (packet 99)
- **Files created:** `WiredTriggerAvatarClicksFurni.java`, `ClickFurniEvent.java`, `FurnitureSingleClickComposer.ts`

---

## Adding a New Trigger — Checklist

- [ ] `WiredTriggerType.java` — add `YOUR_TYPE(N)` with next available code
- [ ] `WiredEvent.java` — add `YOUR_EVENT(WiredTriggerType.YOUR_TYPE)` to Type enum
- [ ] `WiredEvents.java` — add factory method `yourEvent(room, user, item)`
- [ ] `WiredManager.java` — add `triggerYourEvent(room, user, item)` static method
- [ ] `YourTriggerClass.java` — create in `interactions/wired/triggers/`
- [ ] `Incoming.java` — add header constant if using a new packet
- [ ] `YourPacketEvent.java` — create handler if using a new packet
- [ ] `PacketManager.java` — register handler
- [ ] Build: `mvn clean package -DskipTests`, restart emulator
- [ ] `FurnitureYourComposer.ts` — create in `outgoing/room/furniture/logic/`
- [ ] `logic/index.ts` — add `export * from './FurnitureYourComposer'`
- [ ] `OutgoingHeader.ts` — add header constant
- [ ] `NitroMessages.ts` — import + register in `_composers.set()`
- [ ] `RoomObjectEventHandler.ts` — hook into the right mouse/event type
- [ ] Build: `yarn build`, copy `dist/` to nitro-react root (including `index.html`)

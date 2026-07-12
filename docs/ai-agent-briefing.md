# AI Agent Briefing — Habbo Retro Hotel Wired System
## Your Role

You are a senior full-stack developer and patient teacher working on a Habbo retro hotel server. Your job is to:

1. **Implement new wired boxes** (triggers, conditions, effects) end-to-end across two codebases
2. **Teach as you go** — explain every decision, every file, every concept so the developer (Seth) learns deeply, not just copies code
3. **Be precise** — these codebases have many similar-looking files; always specify full paths and explain WHY a change goes in a particular file
4. **Verify your work** — after making any coding suggestions make sure that this is the proper and best path we should take

---

## Project Overview

This is a Habbo Hotel retro server. The goal is to implement **Habbo Wired 2.0** — a visual scripting system that lets room owners create logic chains using furniture boxes. The wired system uses a `TRIGGER → CONDITIONS → EFFECTS` pattern.
In the future we will be implementing SELECTORS & VARIABLES though.

**Current status:** The wired engine infrastructure exists (WiredManager, WiredEvent, WiredEvents, etc.) but many individual boxes are unimplemented or missing. We are building them one by one.

**Completed so far:**
- WiredTrigger: Avatar Clicks Furni (type 15) — fires on single-click, uses custom packet 2789

---

## Tech Stack

### Backend
- **Language:** Java
- **Framework:** Arcturus Morningstar 4.0.0-Beta (a Habbo emulator)
- **Build:** Maven — `mvn clean package -DskipTests`
- **Output:** `target/Arcturus-Morningstar.jar`
- **Config:** MySQL database, `emulator_settings` table

### Frontend
- **Framework:** nitro-react (React + TypeScript)
- **Renderer:** `@nitrots/nitro-renderer` — consumed as TypeScript SOURCE inside `node_modules/` (not a compiled package)
- **Build:** Vite — `yarn build` inside `nitro-react/`
- **Output:** `dist/` folder — copy contents to `nitro-react/` root to deploy
- **Server:** IIS serving from `C:\inetpub\wwwroot\atomcms\public\client\nitro\nitro-react\`

### Communication
- WebSocket binary packets
- Each packet has an integer header ID and a binary payload
- Client sends using Composer classes; server routes by header to MessageHandler classes
- Header IDs must match exactly on both sides

---

## Critical Known Issues / Quirks

### 1. node_modules contains source, not compiled code
`node_modules/@nitrots/nitro-renderer/` is TypeScript source. Vite compiles it during `yarn build`. **Never run `npm install` or `yarn install` — it will wipe custom changes.**

### 2. Barrel export chain is mandatory
Every new TypeScript Composer must be added to its directory's `index.ts`. Without the export, Vite/esbuild silently produces `undefined` for the import — no error, the packet just never sends. Always verify after build by grepping the output JS for the header constant value.

### 3. IIS locks files under wwwroot
Files in `C:\inetpub\wwwroot\` may be locked by IIS. To edit: `iisreset /stop`, edit, `iisreset /start`. Or use bash — some files can be written via the mount even while IIS runs.

### 4. yarn build + index.html copy
After `yarn build`, the `dist/` folder has new asset filenames (content-hash based). **Always copy `index.html` from `dist/` to the nitro-react root.** If old `index.html` stays, the browser loads old JS regardless of what's in `assets/`.

### 5. vite.config.js needs `base: './'`
Without this, Vite generates absolute `/assets/...` paths. IIS serves the app from a subdirectory so absolute paths 404. The fix is already in place — do not change it.

### 6. wired.engine.enabled must be 1
`UPDATE emulator_settings SET value = '1' WHERE key = 'wired.engine.enabled';`
If this is 0, no wired trigger will ever fire.

---

## Key File Locations

### Backend (relative to `Arcturus-Community/src/main/java/com/eu/habbo/`)

| What | Path |
|---|---|
| Wired trigger box classes | `habbohotel/items/interactions/wired/triggers/` |
| Wired trigger type enum | `habbohotel/wired/migrate/WiredTriggerType.java` |
| Wired event type enum | `habbohotel/wired/core/WiredEvent.java` |
| Wired event factories | `habbohotel/wired/migrate/WiredEvents.java` |
| Wired engine entry points | `habbohotel/wired/core/WiredManager.java` |
| Incoming packet handlers | `messages/incoming/rooms/items/` |
| Incoming header constants | `messages/incoming/Incoming.java` |
| Packet handler registration | `messages/PacketManager.java` |

### Frontend (relative to `nitro-react/node_modules/@nitrots/nitro-renderer/src/`)

| What | Path |
|---|---|
| Outgoing header constants | `nitro/communication/messages/outgoing/OutgoingHeader.ts` |
| Outgoing composer classes | `nitro/communication/messages/outgoing/room/furniture/logic/` |
| Composer barrel export | `nitro/communication/messages/outgoing/room/furniture/logic/index.ts` |
| Composer registration | `nitro/communication/NitroMessages.ts` |
| Click event hook | `nitro/room/RoomObjectEventHandler.ts` |
| Furniture click logic | `nitro/room/object/logic/furniture/FurnitureLogic.ts` |

---

## How the Wired Trigger Chain Works (Backend)

1. A packet arrives → `PacketManager` routes it to a `MessageHandler`
2. The handler calls `WiredManager.triggerXxx(room, roomUnit, item)`
3. `WiredManager` creates a `WiredEvent` using the `WiredEvents` factory
4. `WiredManager.handleEvent()` iterates all wired boxes in the room
5. It finds `WiredTriggerFurni` boxes whose `getWiredTriggerType()` matches the event type
6. For each match: checks if the specific item is in the trigger's configured list
7. If yes: evaluates all conditions → if all pass: fires all effects

---

## How Packets Are Sent (Frontend)

1. An event fires in `RoomObjectEventHandler.ts` (e.g., `handleRoomObjectMouseClickEvent`)
2. Code calls `this._roomEngine.connection.send(new FurnitureSingleClickComposer(itemId))`
3. `SocketConnection.send()` calls `getComposerId(composer)` → looks up `composer.constructor` in `_messageIdByComposer` map
4. The map was populated from `NitroMessages.registerMessages()` which iterated `_composers` (header→class) and set class→header
5. The header is found, binary packet is serialized and sent over WebSocket

---

## Template: Creating a New Wired Trigger

### Backend steps (Java)

```java
// 1. WiredTriggerType.java — add enum value with unique code
YOUR_TYPE(N),

// 2. WiredEvent.java — add to Type enum
YOUR_EVENT(WiredTriggerType.YOUR_TYPE),

// 3. WiredEvents.java — add factory
public static WiredEvent yourEventName(Room room, RoomUnit user, HabboItem item) {
    return new WiredEvent(WiredEvent.Type.YOUR_EVENT, room, user, item);
}

// 4. WiredManager.java — add static trigger method
public static boolean triggerYourEvent(Room room, RoomUnit user, HabboItem item) {
    WiredEvent event = WiredEvents.yourEventName(room, user, item);
    return handleEvent(event);
}

// 5. Create WiredTriggerYourName.java
public class WiredTriggerYourName extends WiredTriggerFurni {
    public WiredTriggerYourName(ResultSet set, Item baseItem) throws SQLException {
        super(set, baseItem);
    }
    public WiredTriggerYourName(int id, int userId, Item baseItem)
        throws IllegalAccessException { super(id, userId, baseItem); }

    @Override
    public WiredTriggerType getWiredTriggerType() {
        return WiredTriggerType.YOUR_TYPE;
    }
}

// 6. Incoming.java
public static final int YourPacketEvent = NNNN;  // agree on header with frontend

// 7. YourPacketEvent.java
public class YourPacketEvent extends MessageHandler {
    @Override
    public void handle() throws Exception {
        Room room = this.client.getHabbo().getHabboInfo().getCurrentRoom();
        if (room == null) return;
        int itemId = this.packet.readInt();
        HabboItem item = room.getHabboItem(itemId);
        if (item == null) return;
        WiredManager.triggerYourEvent(room, this.client.getHabbo().getRoomUnit(), item);
    }
}

// 8. PacketManager.java
this.registerHandler(Incoming.YourPacketEvent, YourPacketEvent.class);
```

### Frontend steps (TypeScript)

```typescript
// 1. Create FurnitureYourComposer.ts in outgoing/room/furniture/logic/
import { IMessageComposer } from '../../../../../../../api';
export class FurnitureYourComposer
    implements IMessageComposer<ConstructorParameters<typeof FurnitureYourComposer>> {
    private _data: ConstructorParameters<typeof FurnitureYourComposer>;
    constructor(itemId: number) { this._data = [itemId]; }
    public getMessageArray() { return this._data; }
    public dispose(): void { return; }
}

// 2. logic/index.ts — ADD THIS LINE
export * from './FurnitureYourComposer';

// 3. OutgoingHeader.ts
public static YOUR_ACTION = NNNN;  // same number as Incoming.YourPacketEvent

// 4. NitroMessages.ts — two places:
// In imports: add FurnitureYourComposer to the import from './messages'
// In registerMessages(): 
this._composers.set(OutgoingHeader.YOUR_ACTION, FurnitureYourComposer);

// 5. RoomObjectEventHandler.ts — add to the right event handler:
// For single-click on floor furni: handleRoomObjectMouseClickEvent → OBJECT_UNDEFINED → FLOOR branch
// For double-click: handleRoomObjectMouseDoubleClickEvent
// Import at top: add FurnitureYourComposer to existing import from '../communication'
this._roomEngine.connection.send(new FurnitureYourComposer(event.objectId));
```

---

## Debugging Checklist

When something doesn't fire:

- [ ] Is `wired.engine.enabled = 1` in the database?
- [ ] Is the trigger box placed in the room and configured with the correct furniture?
- [ ] Did the backend rebuild? (`mvn clean package -DskipTests`, emulator restarted?)
- [ ] Is the handler registered in `PacketManager.java`?
- [ ] Did the frontend rebuild? (`yarn build` succeeded?)
- [ ] Was `index.html` copied from `dist/` to the nitro-react root?
- [ ] Is the Composer exported from `logic/index.ts`?
- [ ] Is the Composer registered in `NitroMessages.ts`?
- [ ] Grep the built JS for the header constant value — is it present?
- [ ] Check browser DevTools → Network → WS tab — is the packet being sent?

---

## Docs in This Folder

| File | Contents |
|---|---|
| `ai-agent-briefing.md` | This file — give to AI agents at session start |
| `wired-lookup-table.md` | Trigger/condition/effect type codes, file locations, per-box notes |
| `wired-trigger-study-guide.docx` | Full textbook walkthrough of the Avatar Clicks Furni implementation |

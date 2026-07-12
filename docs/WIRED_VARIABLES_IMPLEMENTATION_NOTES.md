# Wired Variables Implementation Notes

This document records the current Wired Variables work so future agents can continue from the same mental model.

## Habbo Variable Model

Habbo Wired Variables are integer storage boxes. Every value is a signed 64-bit integer (`long` in Java). Variables are not part of a wired execution stack like triggers/effects/conditions. They are passive storage boxes placed anywhere in the room, then referenced by effect/condition boxes.

Known Habbo variable categories:

- User: value attached to a user within a room.
- Furni: value attached to a furni within a room.
- Global: value attached to the room.
- Context: value attached to a wired signal.
- From Another Room: references shared permanent variables from another room.
- Echo: internal variables exposed by Habbo, not user-created variables.

Current implementation only supports Global variables.

Global variable uniqueness rule:

- A room may only have one global variable with a given normalized name.
- Attempting to save another global variable named the same should fail with: `Variable name is already in use in this room, please choose another one!`

Name normalization:

- Lowercase only.
- Spaces become underscores.
- Only letters, numbers, and underscores are allowed.
- Leading/trailing underscores are trimmed.
- Length after normalization must be 1-40.
- Name must include at least one alphanumeric character.
- Examples:
  - `Test Man` becomes `test_man`.
  - `___` is invalid.
  - `dog___________` becomes `dog`.
  - `dog___________cat` remains valid.

Availability/persistence options:

- `0` / `1`: While room is active. Value resets when the room unloads.
- `10`: Permanent. Value survives unload/restart.
- `11`: Permanent, shared across rooms. Same as permanent for the current room, but later should be exposed to From Another Room variable boxes.

The frontend currently displays Habbo's option `1` for "While room is active"; backend maps `1` to the same behavior as `0`.

Pickup behavior:

- Picking up the variable box clears its data.
- For a global variable, that means name, value, and persistence are reset/deleted.
- For future user/furni variables, picking up the defining box should clear every attached value for that variable name.

Overflow behavior:

- Values are intended to behave like Java signed `long`.
- Arithmetic may overflow naturally into negative/positive values.
- Power uses `Math.pow` then casts to `long`, so very large values may clamp/cast according to Java double-to-long behavior.

## Backend Files Added

Global variable core:

- `src/main/java/com/eu/habbo/habbohotel/items/interactions/InteractionWiredVariable.java`
- `src/main/java/com/eu/habbo/habbohotel/items/interactions/wired/variables/WiredVariableGlobal.java`
- `src/main/java/com/eu/habbo/habbohotel/wired/api/IWiredVariable.java`
- `src/main/java/com/eu/habbo/habbohotel/wired/WiredVariableType.java`
- `src/main/java/com/eu/habbo/habbohotel/wired/WiredVariablePersistence.java`
- `src/main/java/com/eu/habbo/habbohotel/wired/variables/WiredVariableName.java`
- `src/main/java/com/eu/habbo/habbohotel/wired/variables/WiredVariableStore.java`

Variable packets:

- `src/main/java/com/eu/habbo/messages/outgoing/wired/WiredVariableDataComposer.java`
- `src/main/java/com/eu/habbo/messages/incoming/wired/WiredVariableSaveDataEvent.java`

Change Variable Value effect:

- `src/main/java/com/eu/habbo/habbohotel/items/interactions/wired/effects/WiredEffectChangeVariableValue.java`

SQL:

- `sqlupdates/4_0_3-beta_TO_4_0_4-beta.sql`

## Backend Files Modified

- `src/main/java/com/eu/habbo/habbohotel/items/ItemManager.java`
  - Registers `wf_var_room` to `WiredVariableGlobal`.
  - User said they added `wf_act_change_var_val` to `WiredEffectChangeVariableValue`.

- `src/main/java/com/eu/habbo/habbohotel/rooms/RoomItemManager.java`
  - Registers/unregisters `InteractionWiredVariable` with `RoomSpecialTypes`.
  - Invalidates wired cache on item moves.

- `src/main/java/com/eu/habbo/habbohotel/rooms/RoomSpecialTypes.java`
  - Added variable collections and lookup helpers:
    - `getVariables()`
    - `getVariables(WiredVariableType type)`
    - `getVariable(int itemId)`
    - `getVariable(WiredVariableType type, String name)`
    - `isVariableNameInUse(...)`
    - `addVariable(...)`
    - `removeVariable(...)`
    - `refreshVariable(...)`
  - Variables are indexed by id and by `type.code + ":" + variableName`.

- `src/main/java/com/eu/habbo/messages/incoming/Incoming.java`
  - Added `WiredVariableSaveDataEvent = 3953`.

- `src/main/java/com/eu/habbo/messages/outgoing/Outgoing.java`
  - Added `WiredVariableDataComposer = 3952`.

- `src/main/java/com/eu/habbo/messages/PacketManager.java`
  - Registers `WiredVariableSaveDataEvent`.

- `src/main/java/com/eu/habbo/habbohotel/wired/WiredEffectType.java`
  - User added `CHANGE_VARIABLE_VALUE(39)`.

## Database Design

Current table: `wired_variables`.

Purpose:

- Store long values for variables that need persistence.
- Also supports future owner-scoped values.

Important columns:

- `item_id`: the variable box item id.
- `room_id`: room where this variable belongs.
- `variable_type`: global/user/furni/etc type code.
- `variable_name`: normalized variable name.
- `persistence`: availability/persistence code.
- `owner_type`: scope owner type, currently global only.
- `owner_id`: scope owner id, currently room/global id style.
- `value`: `BIGINT`, signed 64-bit.

Important uniqueness:

- Current implementation uses uniqueness around `item_id`, `owner_type`, and `owner_id`.
- For future user/furni values, expected keys are:
  - User: `room_id + variable_name + user_id`
  - Furni: `room_id + variable_name + item_id`

Future note:

- Keep variable definition boxes separate from variable values. A user/furni variable box defines the variable name and persistence. The value table should store many rows for many users/items under that definition.

## Global Variable Backend Behavior

`InteractionWiredVariable`:

- Opens variable editor with `WiredVariableDataComposer`.
- Is walkable.
- Does not execute as a stack item.
- Serializes a Nitro wired definition prefix, then variable data.
- On pickup, deletes stored values and resets name/persistence/value.

`WiredVariableGlobal`:

- Type is `WiredVariableType.GLOBAL`.
- Configured through `configure(name, persistence, value)`.

`WiredVariableSaveDataEvent`:

- Reads item id.
- Checks room rights.
- Finds the variable box through `room.getRoomSpecialTypes().getVariable(itemId)`.
- Reads persistence/name/value.
- Normalizes and validates name.
- Enforces unique name per room/type.
- Saves config and value.
- Sends `WiredSavedComposer`.
- Invalidates wired cache.

Packet ids:

- Server to client open data: `3952`.
- Client to server save data: `3953`.

Variable open packet shape currently matches Nitro's `Triggerable` prefix so the React/Nitro parser can use a normal wired definition parser:

```text
boolean stuffTypeSelectionEnabled
int furniLimit
int selectedItemCount
int spriteId
int itemId
string stringData
int intParamCount
int... intParams
int stuffTypeSelectionCode
int variableType
string variableName
int persistence
string value
```

The variable value is sent as a string to avoid JS number precision problems in the client.

## Change Variable Value Effect

Furniture name:

- `wf_act_change_var_val`

Effect type:

- `WiredEffectType.CHANGE_VARIABLE_VALUE(39)`

Current scope:

- Only global variables are supported.
- UI shows furni/user/global/context icons, but only global is enabled.
- Destination/reference source controls are locked to global until user/furni/context variables exist.

Stored config:

- Numeric choices are saved in `intParams`.
- Names and reference value are saved in JSON `stringParam`.
- `wired_data` stores JSON for reload.

Current int param layout:

```text
0 targetVariableType
1 operation
2 referenceMode
3 referenceVariableType
4 destinationSource
5 referenceSource
```

Current string param JSON:

```json
{
  "targetVariable": "dog",
  "referenceVariable": "",
  "referenceValue": 55
}
```

When opening the box, backend sends JSON in `stringData` that also includes the room's current global variable list:

```json
{
  "targetVariable": "dog",
  "referenceVariable": "",
  "referenceValue": 55,
  "globalVariables": ["dog", "users_visited"],
  "targetVariableType": 1,
  "operation": 0,
  "referenceMode": 0,
  "referenceVariableType": 1,
  "destinationSource": 1,
  "referenceSource": 1,
  "delay": 0
}
```

Execution:

- Finds the target global variable by normalized name.
- Gets reference value from:
  - set value, or
  - another global variable if `referenceMode == from variable`.
- Applies operation.
- Calls `target.setValue(newValue)`.
- Calls `target.needsUpdate(true)` and schedules the target.
- Calls `target.activateBox(room, roomUnit, System.currentTimeMillis())` so the variable box animates when updated.

Important fix already made:

- Both `execute(WiredContext)` and legacy `execute(RoomUnit, Room, Object[])` call the same `changeVariable(...)` method.
- This matters because the emulator may run legacy/parallel wired execution depending on config.

Operations:

```text
0   Assign
1   Add
2   Subtract
3   Multiply
4   Divide
5   Power
6   Modulo
40  Set minimum
41  Set maximum
50  Random with upper bound
60  Absolute value
100 Bitwise AND
101 Bitwise OR
102 Bitwise XOR
103 Bitwise NOT
104 Left shift
105 Right shift
110 Bit count
```

Current edge behavior:

- Divide/modulo by zero returns the current value unchanged.
- Random with upper bound returns `0` if bound is `<= 0`, otherwise random `0..bound`.
- Shift operations use Java long shifts.
- Bit count returns `Long.bitCount(current)`.

Potential naming caveat:

- "Set minimum" currently behaves as `Math.max(current, reference)`.
- "Set maximum" currently behaves as `Math.min(current, reference)`.
- This matches clamp-style wording: minimum means value cannot go below reference; maximum means value cannot go above reference.

## Frontend Files Added/Modified

Path:

- `C:\Users\steet\Desktop\nitro-react`

Variable box UI:

- `src/api/wired/WiredVariableLayoutCode.ts`
- `src/api/wired/index.ts`
- `src/components/wired/views/variables/WiredVariableBaseView.tsx`
- `src/components/wired/views/variables/WiredVariableGlobalView.tsx`
- `src/components/wired/views/variables/WiredVariableLayoutView.tsx`
- `src/components/wired/WiredView.tsx`
- `src/hooks/wired/useWired.ts`

Change Variable Value effect UI:

- `src/components/wired/views/actions/WiredEffectChangeVariableValueView.tsx`
- `src/components/wired/views/actions/WiredActionLayoutView.tsx`
- `src/api/wired/WiredActionLayoutCode.ts`

Renderer packet classes in `node_modules/@nitrots/nitro-renderer`:

- `src/nitro/communication/messages/parser/roomevents/VariableDefinition.ts`
- `src/nitro/communication/messages/parser/roomevents/WiredFurniVariableParser.ts`
- `src/nitro/communication/messages/incoming/roomevents/WiredFurniVariableEvent.ts`
- `src/nitro/communication/messages/outgoing/roomevents/UpdateVariableMessageComposer.ts`

Renderer barrels/registration modified:

- `src/nitro/communication/messages/parser/roomevents/index.ts`
- `src/nitro/communication/messages/incoming/roomevents/index.ts`
- `src/nitro/communication/messages/outgoing/roomevents/index.ts`
- `src/nitro/communication/messages/incoming/IncomingHeader.ts`
- `src/nitro/communication/messages/outgoing/OutgoingHeader.ts`
- `src/nitro/communication/NitroMessages.ts`

Important deployment note:

- The user also has an IIS build copy at:
  - `C:\inetpub\wwwroot\atomcms\public\client\nitro\nitro-react`
- If frontend source is copied there, the edited renderer `node_modules/@nitrots/nitro-renderer` must also be copied or Rollup will fail with missing exports:
  - `VariableDefinition`
  - `WiredFurniVariableEvent`
  - `UpdateVariableMessageComposer`

## Frontend Variable Box Behavior

Global variable box UI includes:

- Variable name input.
- Current value display.
- Availability radio buttons:
  - While room is active.
  - Permanent.
  - Permanent, shared across rooms.

Localization keys expected:

```json
{
  "wiredfurni.params.variables.variable_name": "Variable name:",
  "wiredfurni.params.variables.inspection": "Variable Inspection",
  "wiredfurni.params.variables.inspection.current_value": "Current value: %value%",
  "wiredfurni.params.variables.availability": "Availability options:",
  "wiredfurni.params.variables.availability.1": "While room is active",
  "wiredfurni.params.variables.availability.10": "Permanent",
  "wiredfurni.params.variables.availability.11": "Permanent, shared across rooms"
}
```

## Frontend Change Variable Value UI

Current UI includes:

- Choose variable:
  - Shows furni/user/global/context icons.
  - Only global is enabled.
  - Dropdown defaults to `wiredfurni.variable_picker.search` in lighter gray.
  - Once selected, shows variable name.

- Operation:
  - Uses `WiredNativeSelect` because custom `WiredSelect` was not selecting reliably here.
  - Basic list shows assign/add/subtract/multiply/divide/power/modulo.
  - `...show advanced` expands the list to include advanced operations.

- Reference value:
  - Radio for set value, with numeric input beside it.
  - Radio for from variable, with source icons and variable dropdown.
  - Uses the same inline pattern as `WiredSelectorFilterXFurniView`.

- Delay:
  - Uses the normal action delay slider.

- Source panel:
  - Always visible.
  - Destination source shows global.
  - Reference source shows global and is dimmed unless reference mode is from variable.

Localization keys expected for effect:

```json
{
  "wiredfurni.variable_picker.search": "Search variables",
  "wiredfurni.params.variables.variable_selection": "Choose variable:",
  "wiredfurni.params.variables.operation": "Operation:",
  "wiredfurni.params.variables.operation.0": "Assign",
  "wiredfurni.params.variables.operation.1": "Add",
  "wiredfurni.params.variables.operation.2": "Subtract",
  "wiredfurni.params.variables.operation.3": "Multiply",
  "wiredfurni.params.variables.operation.4": "Divide",
  "wiredfurni.params.variables.operation.5": "Power",
  "wiredfurni.params.variables.operation.6": "Modulo",
  "wiredfurni.params.variables.operation.advanced": "...show advanced",
  "wiredfurni.params.variables.operation.40": "Set minimum",
  "wiredfurni.params.variables.operation.41": "Set maximum",
  "wiredfurni.params.variables.operation.50": "Random with upper bound",
  "wiredfurni.params.variables.operation.60": "Absolute value",
  "wiredfurni.params.variables.operation.100": "Bitwise AND",
  "wiredfurni.params.variables.operation.101": "Bitwise OR",
  "wiredfurni.params.variables.operation.102": "Bitwise XOR",
  "wiredfurni.params.variables.operation.103": "Bitwise NOT",
  "wiredfurni.params.variables.operation.104": "Left shift (<<)",
  "wiredfurni.params.variables.operation.105": "Right shift (>>)",
  "wiredfurni.params.variables.operation.110": "Bit count",
  "wiredfurni.params.variables.reference_value": "Reference value",
  "wiredfurni.params.variables.reference_value.set_value": "Set value",
  "wiredfurni.params.variables.reference_value.from_variable": "From variable",
  "wiredfurni.params.sources.global": "Global",
  "wiredfurni.params.sources.merged.title.variables_destination": "Variable destination",
  "wiredfurni.params.sources.merged.title.variables_reference": "Variable reference"
}
```

## Important Frontend Implementation Notes

Nitro's connection only sends/receives composers/events registered in `@nitrots/nitro-renderer`.

That is why the variable packet classes were added under renderer `node_modules`, not only under app `src/api/wired`.

If future work adds new packets, remember to:

- Add parser/event/composer classes.
- Export them through the correct `index.ts` barrels.
- Add incoming/outgoing header ids.
- Register event/composer in `NitroMessages.ts`.

For app-level wired UI routing:

- `useWired.ts` listens for event classes and stores `trigger`.
- `WiredView.tsx` switches on definition instance type.
- Action layouts are routed through `WiredActionLayoutView.tsx`.
- Variable layouts are routed through `WiredVariableLayoutView.tsx`.

## Future User/Furni Variables

Expected backend work:

- Add `WiredVariableType.USER`.
- Add `WiredVariableType.FURNI`.
- Create variable boxes for corresponding furni names when known.
- Add `InteractionWiredVariable` subclasses for each type.
- Store variable definitions on the box, but values per owner in `wired_variables`.
- User value key should include room, variable name/definition, and user id.
- Furni value key should include room, variable name/definition, and item id.
- Picking up the variable box should delete every value under that definition.

Expected frontend work:

- Enable user/furni icons.
- Filter variable dropdown by selected variable type.
- For destination source:
  - User variable should allow triggering user, selected users, selector, signal, etc.
  - Furni variable should allow selected furni, selector, signal, etc.
- For reference source:
  - Same concept, but only active when reference value is "from variable".

Important design warning:

- Do not treat user/furni variable values as a single value on the variable box.
- The box defines the variable. The values live on each user/furni owner.

## Future From Another Room Variables

Shared permanent globals use persistence `11`.

Expected behavior:

- Only variables marked shared permanent are visible to other rooms.
- From Another Room variable box should select source room + shared variable.
- Non-shared permanent variables should not appear in the cross-room picker.

Database likely already has enough fields to support cross-room lookups, but selection/listing packets and UI still need to be designed.

## Future Echo Variables

Echo variables are internal variables, not user-created definitions.

Likely implementation:

- A registry of echo variable providers.
- Values are computed on demand.
- No `wired_variables` row unless caching is explicitly needed.

## Testing Checklist

Global variable box:

- Place `wf_var_room`.
- Open it.
- Save `dog`, active-room persistence.
- Reopen and confirm name/value.
- Try placing second `wf_var_room` with name `dog`; save should fail.
- Pick up original box; value/name should clear.

Change variable value:

- Place trigger + `wf_act_change_var_val` in stack.
- Choose global variable `dog`.
- Operation Assign.
- Set value `55`.
- Trigger stack.
- Expected:
  - Effect box animates.
  - Global variable box animates.
  - Reopen global variable box, current value shows `55`.
- Test Add/Subtract/Multiply after assign works.
- Test persistence by unloading/reloading room:
  - Active-room variable resets.
  - Permanent/shared permanent survives.

Debug tips:

- If the effect box animates but value does not change, check saved `wired_data` for the effect.
- If neither effect nor variable box animates, check whether the item interaction and effect type are registered.
- If frontend cannot open the effect, check `WiredActionLayoutCode` and `WiredActionLayoutView`.
- If frontend build fails with missing renderer exports, copy the edited `@nitrots/nitro-renderer` package into the build copy.
- If value changes but UI still shows old value, check `WiredVariableDataComposer` serialization and client parser.

## Known Current Limitations

- Only global variables exist.
- Change Variable Value only resolves globals.
- Variable picker list is provided through the effect open packet, not a live search/fetch endpoint.
- No string values by design; variables are signed 64-bit integers.
- No From Another Room or Echo behavior yet.
- No user/furni owner value storage logic yet, though database design anticipates it.


# Malicious-input corpus

SC-260. An add-on is untrusted input, and on a client it may have arrived **from the server the user
just joined** (SC-270 §9). That second case is what makes this directory necessary rather than
prudent: installing SweetCookie must not mean executing content from anyone whose server you visit.

Every case here asserts four things, not one:

1. the specific diagnostic is emitted;
2. **the game keeps running** — constitution rule 1;
3. unaffected content in the same pack still loads;
4. nothing was written outside the intended directory.

Point 3 is the one most often forgotten. A malicious file must not take its neighbours down with it.

## Planned cases

| Case | Attack | Limit |
|---|---|---|
| `zip_slip` | entry paths escaping the extraction root | SC-100 §3 |
| `zip_absolute_path` | absolute paths and drive letters | SC-100 §3 |
| `zip_symlink` | symlink entries | SC-100 §3 |
| `zip_bomb_ratio` | 200:1 compression on one entry | SC-100 §3 |
| `zip_bomb_total` | 512 MiB uncompressed | SC-100 §3 |
| `zip_nested_deep` | archive within archive beyond depth 3 | SC-100 §3 |
| `zip_unicode_collision` | non-NFC names colliding case-insensitively | SC-100 §3 |
| `json_deep_nesting` | stack exhaustion in the parser | SC-260 §3 |
| `geometry_vertex_bomb` | a million cubes — client-side denial of service | SC-260 §3 |
| `animation_keyframe_bomb` | unbounded keyframes | SC-260 §3 |
| `particle_rate_bomb` | an emitter rate that fills the world | SC-260 §3 |
| `molang_depth_bomb` | expression nesting beyond the compile limit | SC-260 §3 |
| `molang_infinite_loop` | `loop()` with an unbounded count | SC-260 §3 |
| `entity_event_recursion` | an event that triggers itself | SC-260 §3 |
| `nbt_bomb` | deep or oversized `.mcstructure` NBT | SC-260 §3 |
| `sideband_oversize` | an inbound payload past the cap | SC-260 §5 |
| `pack_hash_mismatch` | a server offering a pack whose hash does not match its handshake | SC-270 §9 |

## Originality

Constitution rule 10 applies here too. These are hostile inputs **we constructed**, not samples
collected from anywhere. Several are generated at test time rather than committed, because a
512 MiB zip bomb has no business being in a git repository — the generator lives beside the case and
is deterministic.

# Frontend design

For management/workspace UI, use the supplied Microsoft Fabric references and
[Fluent 2](https://fluent2.microsoft.design/) for typography, spacing, surfaces and
control hierarchy, retaining Parlor green. Also consult the saved FortiGate,
FortiSIEM and FortiSOAR references in `~/fortinet-research`. Use their
dense-content patterns for navigation, toolbars, tables, filters, forms and dialogs:
compact spacing, readable hierarchy, restrained selection states and controls next
to the data they affect. Avoid decorative accent rails and nested dashboard cards.
Preserve sample data and its visibility during design-only changes.
Keep management copy terse. Do not expose sample/demo labels, session-local storage,
backend wiring, or other implementation details in user-facing text or accessible captions.
Keep the management overview's summary metrics, floor table, upcoming arrivals,
service queue and pantry watch visible together. Preserve their direct actions when
polishing the layout; do not replace this overview with shortcut tiles or tabbed panels.

Keep products immediately visible. Use readable, high-contrast type and factual
copy. Do not reintroduce promotional heroes, decorative accent punctuation,
sparkles, tiny tracked uppercase labels, arbitrary pastel card colors, or filler
taglines. Preserve the user-approved green header, gold rule and centered Parlor
wordmark with suit marks; use readable cream navigation text. Avoid status dots
for rental/retail information: use clear labels and values. A functional product
grid is appropriate. The header should expose language, account and cart controls;
do not restore the redundant top menu links or visible Game library heading. Rental browsing shows branch library membership only; live copy counts and
“Currently rented” belong only in a date/time-aware reservation or arrival flow.
Keep development/preview notices out of customer-facing copy. Avoid redundant edition captions on product
cards and use compact cart spacing with a single subtotal divider. Storefront retail is live: branches, branch prices
and stock, the server cart, card checkout and My Orders use the API, and the server is authoritative
for prices, stock and totals. Card details go only to payment tokenization and are never stored or
logged. PromptPay, bank transfer, cash on delivery, carriers and shipping fees have no backend; do
not reintroduce them as working options. Reservation and staff workspace screens still use sample data.

Prefer short CSS transitions for state changes and interactions. Respect reduced
motion and keep content usable immediately. Use GSAP only when a specific complex
animation needs it; ordinary hover, filtering, menu and dialog motion do not.

Keep the store selector visible above search in every catalog mode. First visit
choices are online, a named branch or just browsing. Global changes require an
explicit save; product edition/quantity controls must not change site context.
Product branch comparisons are read-only. Preserve cart lines on branch changes,
show shortages and block checkout until resolved. Use a single left-aligned card
price without redundant New copy captions or empty split-column price layouts.

Product details live at `/games/:gameId`, with Savantia-inspired media/purchase
columns and expandable details. Keep catalog filters on return. Branch selection
uses a native dropdown (search for longer lists), not one repeated panel per
branch; keep the selected branch while filtering and apply only on explicit save.

Cart edits must never change the catalog/product page behind the drawer. Product
stock uses `stores/store-context.ts`, with no dependency on cart fulfillment,
issues or quantities. Store selection is for browsing; it never rewrites an existing
order. Remove all purchase/detail buttons from catalog cards: images/titles link
to products. Quantity sits above full-width purchase actions, without Review cart
links. Use Savantia's requested button/accordion/drawer motion in CSS and honor
reduced motion; closed accordion content must be inert and aria-hidden.

Do not show live rental availability or an Available now filter in discovery,
product pages or branch comparisons. Use branch library membership, independent
of sealed retail stock. Query rental availability only after visit branch/date/
start/duration selection, and revalidate it on confirmation. Walk-ins need an
arrival-time check; never imply that customers must reserve to browse or visit.

Catalog scrolling uses sticky green
navigation and compact search, normal document scrolling for location/results/cards,
viewport-constrained desktop filters, and a native modal filter drawer at 700px or
below. Do not create a separate scrolling game grid. Keep sidebar highlight/focus
padding inside its scrollport and preserve native select semantics.

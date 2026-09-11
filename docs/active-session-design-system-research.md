# Active Session UI System

## Design objective

The active-session screen is a transactional status page, not a marketing dashboard. Its interface should let a client answer four questions in order: whether the session is active, where it is taking place, how long it has been running, and what it currently costs. Pricing details and assistance actions support that primary task.

The design should therefore feel quiet, deliberate, and operational. Brand color identifies status, key values, and the primary action. It should not be used as general decoration.

## Evidence

### Layout

Material Design's supporting-pane pattern assigns roughly two-thirds of an expanded layout to primary content and the remainder to supporting content.[^1] GOV.UK similarly recommends two-thirds/one-third layouts for primary and secondary content, with a single-column layout as the small-screen starting point.[^2]

For this page, elapsed time and accumulated fee form the primary pane. Pricing detail and actions form the supporting pane. The desktop grid uses a 2:1 ratio and collapses to one column below the existing application breakpoint.

### Surface and elevation

Atlassian defines a bordered default surface as the baseline treatment and reserves raised elevation for movable cards or a deliberately limited focal section. Its guidance warns that excessive raised surfaces create visual noise.[^3] Carbon's base tiles also sit on the same plane as the page and use containment to group related information rather than elevation for its own sake.[^4]

The page consequently uses one bordered surface per meaningful group and no shadows for static content. A shadow remains only on the end-session confirmation because it is an overlay. Nested gradients, glows, decorative icon boxes, and repeated rounded tiles are excluded.

### Spacing and density

Atlassian's spacing system uses an 8-pixel base unit and a limited scale to create rhythm and reduce arbitrary decisions.[^5] Small values are intended for icon/text gaps and compact controls; medium values are intended for component padding and card content.

The local scale is 8, 16, 24, and 32 pixels. Component padding, grid gaps, labels, and action spacing use these values or a deliberate half-step only where optical balance requires it.

### Typography and numeric data

The GOV.UK type scale uses a constrained set of text sizes and recommends tabular numerals for numeric information.[^6] The active-session hierarchy uses:

- page title: 26–33 pixels responsively;
- primary timer: 38–51 pixels with tabular numerals;
- secondary fee: 27–34 pixels with tabular numerals;
- headings and body text: 12–17 pixels according to role.

The timer is the only display-sized value. The accumulated fee remains visibly secondary.

### Color and accessibility

WCAG 2.2 requires at least 4.5:1 contrast for normal text and 3:1 for large text.[^7] Pointer targets should be at least 24 by 24 CSS pixels or have sufficient separation.[^8] Color must not be the only status signal.

The Active state combines text with a dot, buttons retain visible text labels, and keyboard focus uses a high-contrast outline. Action targets remain 44 pixels tall. The interface uses the product teal `#497883` for semantic emphasis, with neutral text, borders, and backgrounds carrying most of the page.

## Component specification

### Page

- Maximum width: 70rem.
- Desktop layout: 2fr primary pane and 1fr supporting pane.
- Mobile layout: one column in reading order.
- Page title has no decorative kicker or accent stroke.

### Session overview

- One flat bordered container.
- Context is a plain header row separated by one rule.
- Active status, location, and table use inline icons only where they improve recognition.
- Elapsed time uses a quiet neutral surface to establish primary emphasis without elevation.
- Accumulated fee sits on the default surface below a divider and uses a smaller numeric style.

### Fee breakdown

- One flat bordered container.
- A single semantic receipt icon accompanies the section title.
- Pricing data uses a definition list with row separators, not three independent mini-cards.
- The refresh note is separated as supporting metadata.

### Actions

- Call Staff is the single filled primary action.
- End Playing is a neutral outlined action.
- Hover changes surface color without movement or elevation.
- Confirmation is the only overlay and may use a shadow to communicate its layer.

## Rejected patterns

- Decorative gradients and glows.
- Shadows on every container.
- Icons placed inside filled rounded squares by default.
- Pills for ordinary metadata.
- Multiple nested cards that repeat the same border and radius.
- Oversized typography for more than one metric.
- Hover movement on static page actions.

## Sources

[^1]: Google, [Material Design 3: Canonical layout examples](https://m3.material.io/foundations/layout/canonical-examples/overview).

[^2]: Government Digital Service, [GOV.UK Design System: Layout](https://design-system.service.gov.uk/styles/layout/).

[^3]: Atlassian, [Atlassian Design System: Elevation](https://atlassian.design/foundations/elevation).

[^4]: IBM, [Carbon Design System: Tile usage](https://carbondesignsystem.com/components/tile/usage/).

[^5]: Atlassian, [Atlassian Design System: Spacing](https://atlassian.design/foundations/spacing).

[^6]: Government Digital Service, [GOV.UK Design System: Type scale](https://design-system.service.gov.uk/styles/type-scale/).

[^7]: W3C, [WCAG 2.2, Success Criterion 1.4.3: Contrast (Minimum)](https://www.w3.org/TR/WCAG22/#contrast-minimum).

[^8]: W3C, [Understanding Success Criterion 2.5.8: Target Size (Minimum)](https://www.w3.org/WAI/WCAG22/Understanding/target-size-minimum).

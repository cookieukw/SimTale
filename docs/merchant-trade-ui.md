# Merchant trade UI

`src/main/resources/Common/UI/Custom/MerchantTrade/MerchantTrade.ui` is an
autonomous presentation mockup for the merchant shop. It follows the existing
SimTale theme, panel, separator, row, tab, and button conventions.

## Integration handoff

When the trade backend is ready, a new `MerchantTradePage` can append
`MerchantTrade/MerchantTrade.ui`, populate `#Title`, `#Subtitle`, `#Balance`,
`#Row0` through `#Row4`, and bind `#BtnBuy`, `#TabFeatured`,
`#TabMaterials`, `#TabCurios`, and `#BtnClose`. The page can then be opened from
the merchant interaction flow. This change deliberately does not modify
`NPCInteractionPage`, merchant visitor/stock systems, profession, currency, or
command code.

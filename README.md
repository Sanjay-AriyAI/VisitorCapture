# Visitor Capture (Android)
Kotlin + Jetpack Compose, Room (SQLite) in `visitors.db`, ML Kit on-device OCR for business cards,
CameraX + ML Kit barcode scanning for staff login. Works fully offline.

## Flow
Staff login (scan vCard QR) -> Welcome -> Capture (camera / skip) -> Processing -> Details -> Customer profile (customer type + market exposure) ->
Product profile (machine / make / model / retrofit kit) -> Lead outcome & follow-up -> Done (auto-resets after 15 s).
Top progress rail: Scan - Details - Customer - Products - Outcome - Done. Gear icon = staff panel.

## Staff login and "attended by"
- First launch shows the Staff login screen. The staff member scans their vCard (or MeCard) QR code; the camera runs live and
  decodes it on the phone. Name, title, company, email and phone are read from the code.
- The employee is saved in the `employees` table (first/last login, raw QR text for audit) and remembered on the phone,
  so a restart does not force a new login. "Not you? Switch user" on the Welcome screen (or Switch user in the staff panel) scans another code.
- Every visitor row stores the employee who attended: `attendedByKey/Name/Title/Email/Phone`. The key is the email
  (else phone digits, else name), so the same person is recognised on every phone.
- "Can't scan? Enter my details manually" is the fallback for a damaged QR code.

## What is recorded for each visitor
- Visit date and time: `createdAt` (UTC milliseconds), stamped when the staff member taps "Scan business card", i.e. when the visit starts.
  Shown in the staff panel and on the Done screen; exported as VisitDate, VisitTime, VisitedAt (ISO 8601 with offset) and TimestampMs.
- Address, State / Province and Country (if applicable): typed on the Details screen, pre-filled from the card photo when the
  text can be recognised (India states imply country India; see `Places` in `CardParser.kt`).
- Market exposure: "Domestic Visitor" or "International Visitor", chosen on the Customer screen under Customer type (both are required to continue). Column `marketExposure`, CSV column `MarketExposure`.
- Additional product interest / models: an optional text area on the Done screen, saved onto the visitor just logged (column `additionalInterest`,
  CSV column `AdditionalInterest`). It saves itself shortly after typing stops, and typing keeps the screen open (30 s after the last keystroke
  instead of 15 s).
- Lead outcome & follow-up, filled in by staff on the Outcome screen (Finish needs at least one outcome):
  - Lead outcome, any of Product Enquiry / Sample Request / Spare Parts Requirement / Order / Factory Visit / Follow-up.
    Column `leadOutcomes` (joined with "; "), CSV column `LeadOutcome`.
  - Discussion notes, typed. Column `discussionNotes`, CSV `DiscussionNotes`.
  - Remarks: Call Back / Factory Visit / Need Follow-up / Others, with free text for Others. Columns `remarks`, `remarksOther`,
    CSV `Remarks`, `RemarksOther`.
  - Follow-up, shown only when Factory Visit or Follow-up is ticked: Send Quotation / Schedule Factory Visit /
    Automatic E-mail Reminder (each with a date and time) or Remarks (free text). Columns `followUpAction`, `followUpAt`
    (epoch ms, 0 = none), `followUpNote`; CSV `FollowUpAction`, `FollowUpDate` (local yyyy-MM-dd HH:mm), `FollowUpAtMs`, `FollowUpNote`.
    Send Quotation has a "Draft quotation email" button: it opens the phone's email app addressed to the visitor with the
    products they asked about; staff attach the quotation and send. Schedule Factory Visit and Automatic E-mail Reminder have an
    "Add to calendar" button: it opens the calendar app with the event filled in (a factory visit invites the visitor as a guest,
    a reminder is for the staff member only) and the calendar app sends the reminders. No extra permissions; the app itself sends nothing.
- Name, title, company, phone, email, customer type, products, other detail, card photo, device name.

## Several devices
- Every entry gets a UUID and the device name (set it in the staff panel on each phone).
- Staff panel -> Export (zip): visitors.csv + card photos. Share it by email, Drive, WhatsApp, USB...
- Staff panel -> Import / merge: pick one or more exports from other phones; duplicates are skipped by UUID.
- On a PC: `python tools/merge_visitors.py out_dir a.zip b.zip c.zip` -> merged_visitors.csv + photos/.

## Products
Products -> tap Mechanical Spares / Electronics Spares / Retrofits -> expand a machine -> tick any number of makes, models
or retrofit kits (catalogue lives in `Catalog.kt`, easy to edit). Each selection is stored as a path such as
`Retrofits > Carding > Retrofit > HMI conversion`, so the CSV can be filtered by product, machine, make or kit.

## Database versions
v2 adds the address fields, the attended-by columns and the `employees` table (`MIGRATION_1_2`). v3 adds
`marketExposure` (`MIGRATION_2_3`). v4 adds `additionalInterest` (`MIGRATION_3_4`). v5 (this release) adds the lead
outcome & follow-up columns (`MIGRATION_4_5`). All migrations in `Db.kt` upgrade in place and keep existing visitors; old rows have blank
new values.
Any later schema change needs a new Migration.

## Tests
`app/src/test/.../ParsersTest.kt` has JUnit tests for the vCard reader and the business-card parser, and
`SyncTest.kt` checks the CSV export round-trips the lead outcome columns
(run with `./gradlew test` or from Android Studio). The parsers are plain Kotlin with no Android dependencies.

Open in Android Studio (Koala or newer), sync, Run. Grant the camera permission when asked.

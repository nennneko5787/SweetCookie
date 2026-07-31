// The description layer behind every SweetCookie screen and command. SC-280 §3.1.
//
// In core/ because none of it is Minecraft. What rows exist, what each says, which can be acted on,
// where the lines go and what the keys do are all decided here; a per-version backend does nothing
// but turn a laid-out line into pixels through ViewRenderer.
//
// That is not a tidiness argument. SC-280 §7 requires the screen's behaviour to be testable
// headlessly, and 26.2 replaced the whole screen rendering model - a test that needed a Screen would
// have to be written twice and run on a client. Here it is plain JUnit in seconds.

dependencies {
    api(project(":api"))
    // For Severity alone: a row's badge is a diagnostic severity (SC-240), and re-declaring the
    // three levels here would be a second enum to keep in step with the first.
    api(project(":format"))
}

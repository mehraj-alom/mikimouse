#!/usr/bin/env python3
"""Generate App-Making-Skills-Research.pdf from the markdown source."""
from fpdf import FPDF

class PDF(FPDF):
    def header(self):
        self.set_font("helvetica", "B", 14)
        self.set_text_color(20, 60, 120)
        self.cell(0, 10, "App-Making Skills Research", new_x="LMARGIN", new_y="NEXT", align="C")
        self.set_font("helvetica", "", 9)
        self.set_text_color(100, 100, 100)
        self.cell(0, 6, "Compiled from Android Developers, GeeksforGeeks, XDuce lifecycle guide. Date: 2026-10-17", new_x="LMARGIN", new_y="NEXT", align="C")
        self.ln(4)

    def chapter_title(self, num, title):
        self.set_font("helvetica", "B", 13)
        self.set_text_color(20, 60, 120)
        self.cell(0, 10, f"{num}. {title}", new_x="LMARGIN", new_y="NEXT")
        self.ln(2)

    def section_title(self, title):
        self.set_font("helvetica", "B", 11)
        self.set_text_color(30, 90, 140)
        self.cell(0, 8, title, new_x="LMARGIN", new_y="NEXT")
        self.ln(1)

    def body(self, text, size=10):
        self.set_font("helvetica", "", size)
        self.set_text_color(40, 40, 40)
        self.multi_cell(0, 5.5, text)
        self.ln(2)

    def bullet(self, text, size=10):
        self.set_font("helvetica", "", size)
        self.set_text_color(40, 40, 40)
        self.cell(5, 5.5, "-")
        self.multi_cell(0, 5.5, text)
        self.ln(0.5)

    def table_row(self, col1, col2, size=9):
        self.set_font("helvetica", "", size)
        self.set_text_color(40, 40, 40)
        self.cell(45, 6, col1, border=1)
        self.set_x(50)
        self.multi_cell(0, 6, col2, border=1)
        self.ln(0.5)

pdf = PDF()
pdf.set_auto_page_break(auto=True, margin=15)
pdf.set_margin(15)
pdf.add_page()

# 1. Intro
pdf.chapter_title(1, "What Makes an App-Making Skill Set")
pdf.body("Building a mobile application is not one skill. It is a layered stack across language, "
         "tools, design, and process. The research below covers what a beginner-to-intermediate "
         "practitioner needs, sourced from official Android documentation and public guides.")

# 2. Kotlin
pdf.chapter_title(2, "Language: Kotlin (Official Android Language)")
pdf.body("Google announced Kotlin as an official language for Android development in 2017. Today, "
         "over 60% of professional Android developers use Kotlin, and Google recommends it as the "
         "first choice for new Android projects.")
pdf.section_title("Why Kotlin")
pdf.bullet("Concise code - write less boilerplate than Java.")
pdf.bullet("Null safety - helps avoid NullPointerExceptions; Kotlin Android apps are 20% less likely to crash.")
pdf.bullet("Java interoperability - Kotlin and Java work side by side in the same project.")
pdf.bullet("Modern features - extension functions, data classes, lambdas, coroutines.")
pdf.bullet("Official support - full Android Studio support, including auto-convert Java -> Kotlin.")
pdf.section_title("Where to Learn")
pdf.bullet("Android Developers Kotlin portal: https://developer.android.com/kotlin")
pdf.bullet("Android Basics with Compose course (no experience required): https://developer.android.com/courses/android-basics-compose/course")
pdf.bullet("Kotlin Playground (try in browser): https://developer.android.com/training/kotlinplayground")
pdf.bullet("Official language reference: https://kotlinlang.org/docs/reference/")
pdf.bullet("GeeksforGeeks Kotlin Android Tutorial: https://www.geeksforgeeks.org/kotlin/kotlin-android-tutorial/")

# 3. Tools
pdf.chapter_title(3, "Tools & IDE")
pdf.table_row("Tool", "Purpose")
pdf.table_row("Android Studio", "Official IDE; first-class Kotlin support; built-in emulator (AVD)")
pdf.table_row("Jetpack Compose", "Modern UI toolkit for Android, built on Kotlin")
pdf.table_row("Android SDK", "APIs, build tools, platform packages")
pdf.table_row("Gradle", "Build system; dependencies and compilation")
pdf.table_row("Git / GitHub", "Version control and collaboration")
pdf.body("Download Android Studio: https://developer.android.com/studio")

# 4. Lifecycle
pdf.chapter_title(4, "Mobile App Development Life Cycle (End-to-End)")
lifecycle = [
    ("Stage 1 - Ideation & Strategy", "Define purpose, unique selling point, target audience, and what users want. Without this, there is no foundation."),
    ("Stage 2 - Planning (Roadmap)", "Define scope, objectives, features, technology requirements, timeline, and outsourcing decisions."),
    ("Stage 3 - Design (UX/UI)", "Architecture & workflow, wireframes, technical utility & style, prototyping. Short cycles, quick decisions, test before release."),
    ("Stage 4 - Development", "Front-end (UI the user sees) + back-end (database, server logic). Most expensive and time-consuming stage."),
    ("Stage 5 - Testing & QA", "Usability, accessibility, functional analysis, performance, security. Alpha -> beta -> release phases."),
    ("Stage 6 - Deployment", "Choose single-platform-first or multi-platform release. Publish to Google Play Store and/or Apple App Store."),
    ("Stage 7 - Maintenance", "Bug fixes, new versions, tech support, user feedback. Critical for ratings and long-term success."),
]
for title, desc in lifecycle:
    pdf.section_title(title)
    pdf.body(desc)
pdf.body("Source: XDuce Complete Guide to Mobile App Development Lifecycle 2023 - https://www.xduce.com/the-complete-guide-to-mobile-app-development-lifecycle-2023")

# 5. Core concepts
pdf.chapter_title(5, "Core Android Concepts to Learn")
pdf.table_row("Concept", "What It Is")
pdf.table_row("Activities", "Single screens with a user interface")
pdf.table_row("Services", "Background work without a UI")
pdf.table_row("Content Providers", "Structured data shared between apps")
pdf.table_row("Broadcast Receivers", "Respond to system-wide broadcast announcements")
pdf.table_row("Intents", "Messages to start activities/services or deliver broadcasts")
pdf.table_row("Layouts", "ConstraintLayout, LinearLayout, RelativeLayout, FrameLayout")
pdf.table_row("Views", "TextView, Button, EditText, ImageView, RecyclerView, ScrollView")
pdf.table_row("Manifest", "Declares components, permissions, and app metadata")
pdf.table_row("build.gradle", "Dependencies, build variants, signing config")

# 6. Learning path
pdf.chapter_title(6, "Recommended Learning Path (Beginner)")
pdf.body("1. Kotlin basics - variables, types, control flow, functions, null safety (12 weeks)")
pdf.body("2. Android Studio setup - install SDK, create AVD emulator, run Hello World")
pdf.body("3. First Compose app - build a simple UI with Jetpack Compose")
pdf.body("4. Follow a full tutorial - GeeksforGeeks Kotlin Android tutorial covers Views, layouts, components step by step")
pdf.body("5. Build a small project - todo list, calculator, or note-taking app")
pdf.body("6. Add data - SQLite/Room database or shared preferences")
pdf.body("7. Publish - generate signed APK/AAB and upload to Google Play Console ($25 one-time fee)")

# 7. Skill categories
pdf.chapter_title(7, "Skill Categories Summary")
pdf.table_row("Category", "Skills")
pdf.table_row("Programming", "Kotlin (primary), Java (optional legacy), SQL (databases)")
pdf.table_row("UI/UX", "Jetpack Compose, Material Design, wireframing, prototyping")
pdf.table_row("Tools", "Android Studio, Gradle, Git, GitHub, AVD emulator")
pdf.table_row("Process", "MADL/ADLC - ideation -> design -> code -> test -> deploy -> maintain")
pdf.table_row("Publishing", "Google Play Console, signing keys, app store listings")

# 8. Resources
pdf.chapter_title(8, "Curated Resource Links")
pdf.table_row("Resource", "Link")
pdf.table_row("Android Developers - Kotlin home", "https://developer.android.com/kotlin")
pdf.table_row("Android Basics with Compose course", "https://developer.android.com/courses/android-basics-compose/course")
pdf.table_row("Kotlin Playground (browser)", "https://developer.android.com/training/kotlinplayground")
pdf.table_row("Official Kotlin reference", "https://kotlinlang.org/docs/reference/")
pdf.table_row("Android Studio download", "https://developer.android.com/studio")
pdf.table_row("GeeksforGeeks Kotlin Android Tutorial", "https://www.geeksforgeeks.org/kotlin/kotlin-android-tutorial/")
pdf.table_row("App Development Lifecycle guide", "https://www.xduce.com/the-complete-guide-to-mobile-app-development-lifecycle-2023")

pdf.ln(5)
pdf.set_font("helvetica", "I", 8)
pdf.set_text_color(120, 120, 120)
pdf.multi_cell(0, 4, "Note on Wi-Fi: This session attempted to turn on Wi-Fi but does not have permission to toggle the device radio - only the Wi-Fi settings screen was opened. Please turn Wi-Fi on manually in Settings before opening Chrome to research further. All research above was completed via web search and page fetch, so no Wi-Fi was needed for this document.")

pdf.output("/storage/emulated/0/Download/App-Making-Skills-Research.pdf")
print("PDF written to /storage/emulated/0/Download/App-Making-Skills-Research.pdf")

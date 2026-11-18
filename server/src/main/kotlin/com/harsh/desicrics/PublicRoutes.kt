package com.harsh.desicrics

import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Pages anyone can open without signing in, for links shared outside the app. Phones with the app open
 * these links in it directly; this is what everyone else sees.
 */
fun Route.publicRoutes() {
    get("/join/{code}") {
        val code = call.parameters["code"].orEmpty().filter { it.isLetterOrDigit() }.take(8).uppercase()
        call.respondText(
            page(
                title = "Join a team on DesiCrics",
                body = """
                    <h1>Join a team on DesiCrics</h1>
                    <p>Install DesiCrics, then open <b>Teams</b> and tap <b>Join with a code</b>.</p>
                    <p class="code">$code</p>
                """,
            ),
            ContentType.Text.Html,
        )
    }
}

/** A small mobile-first page in the app's colours. [body] must already be HTML-escaped. */
internal fun page(title: String, body: String): String = """
    <!doctype html>
    <html lang="en">
    <head>
      <meta charset="utf-8">
      <meta name="viewport" content="width=device-width, initial-scale=1">
      <title>${title.escapeHtml()}</title>
      <style>
        body { font-family: system-ui, sans-serif; margin: 0; padding: 24px 16px; background: #f6f7f4; color: #14201a; }
        h1 { font-size: 22px; } .muted { color: #5c6b62; }
        .code { font: 700 32px ui-monospace, monospace; letter-spacing: 4px; color: #0b6b3a; }
        table { width: 100%; border-collapse: collapse; font-size: 14px; }
        th, td { text-align: right; padding: 6px 4px; border-bottom: 1px solid #dde3dc; }
        th:first-child, td:first-child { text-align: left; }
        .card { background: #fff; border-radius: 12px; padding: 16px; margin: 12px 0; }
        @media (prefers-color-scheme: dark) {
          body { background: #0f1512; color: #e7ede9; } .card { background: #18211c; } .muted { color: #9fb0a6; }
          .code { color: #5fd197; } th, td { border-color: #2a352f; }
        }
      </style>
    </head>
    <body>$body</body>
    </html>
""".trimIndent()

internal fun String.escapeHtml(): String =
    replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;")

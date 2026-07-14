package com.udaytank.browse.browser.feed

import com.udaytank.browse.browser.UrlHosts

/** A most-visited page as reported by history. */
data class VisitedUrl(val url: String, val title: String, val visits: Int)

/** A ranked home-screen quick-dial derived from browsing history. */
data class QuickDial(val url: String, val host: String, val label: String)

/** Turns raw visit history into a capped, de-duplicated set of home quick-dials. */
object QuickDialPolicy {

    /**
     * Rank most-visited sites into home quick-dials: aggregate [visited] by registrable host,
     * summing visits; drop hosts in [excludeHosts] (already manual shortcuts); order by total
     * visits desc then most-recent title; cap at [max]. label = host with leading "www." removed.
     * host derived with com.udaytank.browse.browser.UrlHosts.of(url) (returns String? — skip nulls).
     */
    fun rank(visited: List<VisitedUrl>, excludeHosts: Set<String>, max: Int = 8): List<QuickDial> {
        data class Agg(var totalVisits: Int, var topVisits: Int, var url: String, var title: String)

        val byHost = LinkedHashMap<String, Agg>()
        for (v in visited) {
            val host = UrlHosts.of(v.url) ?: continue
            if (host in excludeHosts) continue
            val agg = byHost[host]
            if (agg == null) {
                byHost[host] = Agg(v.visits, v.visits, v.url, v.title)
            } else {
                agg.totalVisits += v.visits
                // Keep the url/title of the host's highest-visit member.
                if (v.visits > agg.topVisits) {
                    agg.topVisits = v.visits
                    agg.url = v.url
                    agg.title = v.title
                }
            }
        }

        return byHost.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Agg>> { it.value.totalVisits }
                    .thenBy { it.value.title },
            )
            .take(max)
            .map { (host, agg) ->
                QuickDial(
                    url = agg.url,
                    host = host,
                    label = host.removePrefix("www."),
                )
            }
    }
}

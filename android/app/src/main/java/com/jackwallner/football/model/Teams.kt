package com.jackwallner.football.model

/** NFL team abbreviations in nflverse form. */
val nflTeamAbbreviations: List<String> = listOf(
    "ARI", "ATL", "BAL", "BUF", "CAR", "CHI", "CIN", "CLE", "DAL", "DEN",
    "DET", "GB", "HOU", "IND", "JAX", "KC", "LA", "LAC", "LV", "MIA",
    "MIN", "NE", "NO", "NYG", "NYJ", "PHI", "PIT", "SEA", "SF", "TB",
    "TEN", "WAS",
)

enum class NFLConference(val label: String) {
    ALL("All"), AFC("AFC"), NFC("NFC");

    fun contains(team: String): Boolean {
        val abbr = normalizedTeamAbbreviation(team)
        return when (this) {
            ALL -> true
            AFC -> abbr in AFC_TEAMS
            NFC -> abbr in NFC_TEAMS
        }
    }

    private companion object {
        val AFC_TEAMS = setOf("BAL", "BUF", "CIN", "CLE", "DEN", "HOU", "IND", "JAX", "KC", "LAC", "LV", "MIA", "NE", "NYJ", "PIT", "TEN")
        val NFC_TEAMS = setOf("ARI", "ATL", "CAR", "CHI", "DAL", "DET", "GB", "LA", "MIN", "NO", "NYG", "PHI", "SEA", "SF", "TB", "WAS")
    }
}

private val TEAM_ALIASES = mapOf(
    "OAK" to "LV", "LVR" to "LV", "SD" to "LAC", "SDG" to "LAC", "STL" to "LA",
    "LAR" to "LA", "WSH" to "WAS", "WFT" to "WAS", "JAC" to "JAX", "GNB" to "GB",
    "KAN" to "KC", "NWE" to "NE", "NOR" to "NO", "SFO" to "SF", "TAM" to "TB",
    "ARZ" to "ARI", "CLV" to "CLE", "HST" to "HOU", "BLT" to "BAL",
    "ARIZONA CARDINALS" to "ARI", "ATLANTA FALCONS" to "ATL", "BALTIMORE RAVENS" to "BAL",
    "BUFFALO BILLS" to "BUF", "CAROLINA PANTHERS" to "CAR", "CHICAGO BEARS" to "CHI",
    "CINCINNATI BENGALS" to "CIN", "CLEVELAND BROWNS" to "CLE", "DALLAS COWBOYS" to "DAL",
    "DENVER BRONCOS" to "DEN", "DETROIT LIONS" to "DET", "GREEN BAY PACKERS" to "GB",
    "HOUSTON TEXANS" to "HOU", "INDIANAPOLIS COLTS" to "IND", "JACKSONVILLE JAGUARS" to "JAX",
    "KANSAS CITY CHIEFS" to "KC", "LOS ANGELES RAMS" to "LA", "LOS ANGELES CHARGERS" to "LAC",
    "LAS VEGAS RAIDERS" to "LV", "MIAMI DOLPHINS" to "MIA", "MINNESOTA VIKINGS" to "MIN",
    "NEW ENGLAND PATRIOTS" to "NE", "NEW ORLEANS SAINTS" to "NO", "NEW YORK GIANTS" to "NYG",
    "NEW YORK JETS" to "NYJ", "PHILADELPHIA EAGLES" to "PHI", "PITTSBURGH STEELERS" to "PIT",
    "SEATTLE SEAHAWKS" to "SEA", "SAN FRANCISCO 49ERS" to "SF", "TAMPA BAY BUCCANEERS" to "TB",
    "TENNESSEE TITANS" to "TEN", "WASHINGTON COMMANDERS" to "WAS",
)

private val TEAM_NAMES = mapOf(
    "ARI" to "Arizona Cardinals", "ATL" to "Atlanta Falcons", "BAL" to "Baltimore Ravens",
    "BUF" to "Buffalo Bills", "CAR" to "Carolina Panthers", "CHI" to "Chicago Bears",
    "CIN" to "Cincinnati Bengals", "CLE" to "Cleveland Browns", "DAL" to "Dallas Cowboys",
    "DEN" to "Denver Broncos", "DET" to "Detroit Lions", "GB" to "Green Bay Packers",
    "HOU" to "Houston Texans", "IND" to "Indianapolis Colts", "JAX" to "Jacksonville Jaguars",
    "KC" to "Kansas City Chiefs", "LA" to "Los Angeles Rams", "LAC" to "Los Angeles Chargers",
    "LV" to "Las Vegas Raiders", "MIA" to "Miami Dolphins", "MIN" to "Minnesota Vikings",
    "NE" to "New England Patriots", "NO" to "New Orleans Saints", "NYG" to "New York Giants",
    "NYJ" to "New York Jets", "PHI" to "Philadelphia Eagles", "PIT" to "Pittsburgh Steelers",
    "SEA" to "Seattle Seahawks", "SF" to "San Francisco 49ers", "TB" to "Tampa Bay Buccaneers",
    "TEN" to "Tennessee Titans", "WAS" to "Washington Commanders",
)

fun normalizedTeamAbbreviation(team: String): String {
    val key = team.trim().uppercase()
    return TEAM_ALIASES[key] ?: key
}

fun teamFullName(abbr: String): String = TEAM_NAMES[normalizedTeamAbbreviation(abbr)] ?: abbr

/** "Seahawks" from "Seattle Seahawks". */
fun teamNickname(abbr: String): String = teamFullName(abbr).split(" ").lastOrNull() ?: abbr

private fun isUnsignedTeam(abbr: String): Boolean {
    val trimmed = abbr.trim().uppercase()
    return trimmed.isEmpty() || trimmed == "TBD" || trimmed == "—" || trimmed == "-"
}

fun displayTeamAbbr(abbr: String): String = if (isUnsignedTeam(abbr)) "FA" else abbr

fun displayTeamFullName(abbr: String): String = if (isUnsignedTeam(abbr)) "Free Agent" else teamFullName(abbr)

package observability.admin.store

import observability.admin.domain.Datasource
import observability.admin.domain.Member
import observability.admin.domain.Team

object SeedData {
    val teams = listOf(
        Team("t_payments", "Payments", "@payments", "#data-payments"),
        Team("t_growth", "Growth Analytics", "@growth-analytics", "#data-growth"),
        Team("t_platform", "Data Platform", "@data-platform", "#data-platform"),
        Team("t_warehouse", "Warehouse", "@warehouse", "#data-warehouse"),
    )

    val members = listOf(
        Member("u_01", "Alex Park", "alex@acme.io", "Data Engineer", listOf("t_payments")),
        Member("u_02", "Maya Chen", "maya@acme.io", "SRE", listOf("t_payments", "t_platform")),
        Member("u_03", "Jordan Reeves", "jordan@acme.io", "Analytics Eng.", listOf("t_growth")),
        Member("u_04", "Priya Shah", "priya@acme.io", "Data Engineer", listOf("t_growth", "t_platform")),
        Member("u_05", "Sam Okafor", "sam@acme.io", "Eng Manager", listOf("t_platform")),
        Member("u_06", "Lina Vasquez", "lina@acme.io", "Data Engineer", listOf("t_warehouse")),
        Member("u_07", "Theo Albright", "theo@acme.io", "Analytics Eng.", listOf("t_warehouse", "t_growth")),
        Member("u_08", "Ren Tanaka", "ren@acme.io", "On-call Eng.", listOf("t_payments")),
        Member("u_09", "Beatrice Woods", "bea@acme.io", "Director", emptyList()),
    )

    val datasources = emptyList<Datasource>()
}

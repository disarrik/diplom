package observability.std.processor

import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import observability.common.notify.NotifyService
import observability.common.processor.LineageProcessor
import observability.common.model.*
import java.util.UUID
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LineageProcessorTest {

    private lateinit var stateService: FakeStateService
    private lateinit var notifyService: NotifyService
    private lateinit var processor: LineageProcessor

    @BeforeTest
    fun setUp() {
        stateService = FakeStateService()
        notifyService = mockk(relaxed = true)
        processor = StdLineageProcessor(stateService, notifyService)
    }

    private fun table(name: String, ns: String = "db") = TableStorageEntity(ns, name)

    private fun lineage(
        sources: List<StorageEntity>,
        targets: List<StorageEntity>,
        type: LineageType = LineageType.UPDATE,
    ) = Lineage(
        id = UUID.randomUUID(),
        sources = sources,
        targets = targets,
        lineageType = type,
    )

    @Test
    fun `UPDATE links source to target`() {
        val src = table("source")
        val tgt = table("target")

        processor.process(lineage(listOf(src), listOf(tgt)))

        val children = stateService.getChildrenRecursively(src)
        assertEquals(listOf(tgt), children)
    }

    @Test
    fun `UPDATE with incident on source notifies target`() {
        val src = table("source")
        val tgt = table("target")
        stateService.registerChange(src, "null_spike")

        processor.process(lineage(listOf(src), listOf(tgt)))

        val incidents = stateService.getActiveIncidentsRecursively(src)
        assertEquals(1, incidents.size)
        verify(exactly = 1) { notifyService.notify(tgt, incidents.first(), false) }
    }

    @Test
    fun `UPDATE without incidents sends no notifications`() {
        val src = table("source")
        val tgt = table("target")

        processor.process(lineage(listOf(src), listOf(tgt)))

        confirmVerified(notifyService)
    }

    @Test
    fun `UPDATE propagates incident to existing children of target`() {
        val src = table("source")
        val tgt = table("target")
        val child = table("child")
        stateService.link(tgt, child)
        stateService.registerChange(src, "schema_change")

        processor.process(lineage(listOf(src), listOf(tgt)))

        val incident = stateService.getActiveIncidentsRecursively(src).single()
        verify(exactly = 1) { notifyService.notify(tgt, incident, false) }
        verify(exactly = 1) { notifyService.notify(child, incident, false) }
    }

    @Test
    fun `UPDATE with multiple sources and targets links all pairs`() {
        val s1 = table("s1")
        val s2 = table("s2")
        val t1 = table("t1")
        val t2 = table("t2")

        processor.process(lineage(listOf(s1, s2), listOf(t1, t2)))

        assertEquals(setOf(t1, t2), stateService.getChildrenRecursively(s1).toSet())
        assertEquals(setOf(t1, t2), stateService.getChildrenRecursively(s2).toSet())
    }

    @Test
    fun `REWRITE disables old incidents on targets before linking`() {
        val oldSrc = table("old_source")
        val tgt = table("target")
        stateService.link(oldSrc, tgt)
        stateService.registerChange(oldSrc, "freshness_violation")
        val incident = stateService.getActiveIncidentsRecursively(tgt).single()

        val newSrc = table("new_source")
        processor.process(lineage(listOf(newSrc), listOf(tgt), LineageType.REWRITE))

        verify(exactly = 1) { notifyService.notify(tgt, incident, true) }
    }

    @Test
    fun `REWRITE still links and notifies for new source incidents`() {
        val newSrc = table("new_source")
        val tgt = table("target")
        stateService.registerChange(newSrc, "null_spike")
        val incident = stateService.getActiveIncidentsRecursively(newSrc).single()

        processor.process(lineage(listOf(newSrc), listOf(tgt), LineageType.REWRITE))

        verify(exactly = 1) { notifyService.notify(tgt, incident, false) }
        val children = stateService.getChildrenRecursively(newSrc)
        assertEquals(setOf(tgt), children.toSet())
    }

    @Test
    fun `incident on ancestor of source propagates through new link`() {
        val grandparent = table("grandparent")
        val src = table("source")
        val tgt = table("target")
        stateService.link(grandparent, src)
        stateService.registerChange(grandparent, "volume_anomaly")

        processor.process(lineage(listOf(src), listOf(tgt)))

        val incident = stateService.getActiveIncidentsRecursively(grandparent).single()
        verify(exactly = 1) { notifyService.notify(tgt, incident, false) }
    }

    @Test
    fun `deep child chain receives notifications`() {
        val src = table("source")
        val tgt = table("target")
        val c1 = table("child1")
        val c2 = table("child2")
        stateService.link(tgt, c1)
        stateService.link(c1, c2)
        stateService.registerChange(src, "schema_change")
        val incident = stateService.getActiveIncidentsRecursively(src).single()

        processor.process(lineage(listOf(src), listOf(tgt)))

        verify(exactly = 1) { notifyService.notify(tgt, incident, false) }
        verify(exactly = 1) { notifyService.notify(c1, incident, false) }
        verify(exactly = 1) { notifyService.notify(c2, incident, false) }
    }

    @Test
    fun `REWRITE disables incidents inherited from ancestor of target`() {
        val ancestor = table("ancestor")
        val tgt = table("target")
        stateService.link(ancestor, tgt)
        stateService.registerChange(ancestor, "data_drift")
        val incident = stateService.getActiveIncidentsRecursively(tgt).single()

        val newSrc = table("new_source")
        processor.process(lineage(listOf(newSrc), listOf(tgt), LineageType.REWRITE))

        verify(exactly = 1) { notifyService.notify(tgt, incident, true) }
    }
}

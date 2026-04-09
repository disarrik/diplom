package observability.std.importer.lineage.marquez

import observability.common.model.LineageType
import observability.common.model.TableStorageEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarquezLineageGraphMapperTest {

    @Test
    fun mapsJobWithTwoSourcesAndOneTarget() {
        val json = """
            {
              "graph": [
                {
                  "id": "dataset:ns:orders",
                  "type": "DATASET",
                  "inEdges": [],
                  "outEdges": [
                    {"origin": "dataset:ns:orders", "destination": "job:ns:etl"}
                  ]
                },
                {
                  "id": "dataset:ns:users",
                  "type": "DATASET",
                  "inEdges": [],
                  "outEdges": [
                    {"origin": "dataset:ns:users", "destination": "job:ns:etl"}
                  ]
                },
                {
                  "id": "job:ns:etl",
                  "type": "JOB",
                  "inEdges": [
                    {"origin": "dataset:ns:orders", "destination": "job:ns:etl"},
                    {"origin": "dataset:ns:users", "destination": "job:ns:etl"}
                  ],
                  "outEdges": [
                    {"origin": "job:ns:etl", "destination": "dataset:ns:fact"}
                  ]
                },
                {
                  "id": "dataset:ns:fact",
                  "type": "DATASET",
                  "inEdges": [
                    {"origin": "job:ns:etl", "destination": "dataset:ns:fact"}
                  ],
                  "outEdges": []
                }
              ]
            }
        """.trimIndent()

        val response = parseJson<LineageGraphResponse>(json)
        val lineages = MarquezLineageGraphMapper.toLineages(response.graph.orEmpty())

        assertEquals(1, lineages.size)
        val ln = lineages[0]
        assertEquals(LineageType.UPDATE, ln.lineageType)
        assertEquals(MarquezLineageGraphMapper.stableLineageId("job:ns:etl"), ln.id)
        assertEquals(
            setOf(
                TableStorageEntity("ns", "orders"),
                TableStorageEntity("ns", "users"),
            ),
            ln.sources.toSet(),
        )
        assertEquals(
            listOf(TableStorageEntity("ns", "fact")),
            ln.targets,
        )
    }

    @Test
    fun skipsJobWithoutBothInputsAndOutputs() {
        val json = """
            {
              "graph": [
                {
                  "id": "dataset:ns:only",
                  "type": "DATASET",
                  "outEdges": [{"origin": "dataset:ns:only", "destination": "job:ns:orphan"}]
                },
                {
                  "id": "job:ns:orphan",
                  "type": "JOB",
                  "inEdges": [{"origin": "dataset:ns:only", "destination": "job:ns:orphan"}],
                  "outEdges": []
                }
              ]
            }
        """.trimIndent()

        val response = parseJson<LineageGraphResponse>(json)
        val lineages = MarquezLineageGraphMapper.toLineages(response.graph.orEmpty())
        assertTrue(lineages.isEmpty())
    }

    @Test
    fun parseDatasetNodeIdSupportsColonInName() {
        val t = MarquezLineageGraphMapper.parseDatasetNodeId("dataset:my_ns:table:with:colons")
        assertEquals(TableStorageEntity("my_ns", "table:with:colons"), t)
    }
}

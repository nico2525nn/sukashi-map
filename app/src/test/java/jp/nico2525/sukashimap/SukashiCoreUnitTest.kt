package jp.nico2525.sukashimap

import jp.nico2525.sukashimap.data.AirlinesCatalog
import jp.nico2525.sukashimap.data.RailwayRepository
import jp.nico2525.sukashimap.data.RoutingAndPlaceService
import jp.nico2525.sukashimap.model.Coord
import jp.nico2525.sukashimap.model.CropAspectPreset
import jp.nico2525.sukashimap.model.CropFrameSettings
import jp.nico2525.sukashimap.model.GeoCoordinate
import jp.nico2525.sukashimap.model.TransportInfo
import jp.nico2525.sukashimap.render.MapProjectionEngine
import jp.nico2525.sukashimap.render.ProjectionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class SukashiCoreUnitTest {

    @Test
    fun projectionSelectsMercatorForDomesticAndRobinsonForTransPacific() {
        val kixOka = listOf(Coord(135.244, 34.427), Coord(127.646, 26.196))
        val domesticProj = MapProjectionEngine.selectProjection(kixOka)
        assertEquals(ProjectionKind.SCALED_MERCATOR, domesticProj.kind)
        assertEquals(false, domesticProj.wrapAntimeridian)

        val hndHnl = listOf(Coord(139.780, 35.552), Coord(-157.922, 21.319))
        val pacificProj = MapProjectionEngine.selectProjection(hndHnl)
        assertEquals(ProjectionKind.ROBINSON, pacificProj.kind)
        assertEquals(true, pacificProj.wrapAntimeridian)
        assertTrue(pacificProj.projectX(-157.922, 21.319) > pacificProj.projectX(139.780, 35.552))

        val westUk = pacificProj.normalizeLon(-0.5)
        val eastUk = pacificProj.normalizeLon(0.5)
        assertTrue(abs(eastUk - westUk) > 170.0)
    }

    @Test
    fun iterativeRdpHandles100kPointsWithoutStackOverflow() {
        val massive = ArrayList<Coord>(100_000)
        for (i in 0 until 100_000) {
            val lon = 135.0 + i * 0.00005
            val lat = 35.0 + sin(i * 0.01) * 0.02
            massive.add(Coord(lon, lat))
        }
        val simplified = RoutingAndPlaceService.simplifyRdpMeters(massive, 150.0)
        assertTrue(simplified.size in 2..2000)
        assertEquals(massive.first(), simplified.first())
        assertEquals(massive.last(), simplified.last())
    }

    @Test
    fun rdpSimplificationReducesCollinearPointsWhilePreservingCorners() {
        val pts = listOf(
            Coord(139.5000, 35.3000),
            Coord(139.5005, 35.3000),
            Coord(139.5010, 35.3000),
            Coord(139.5200, 35.3000),
            Coord(139.5200, 35.3300)
        )
        val simplified = RoutingAndPlaceService.simplifyRdpMeters(pts, 150.0)
        assertEquals(3, simplified.size)
        assertEquals(pts.first(), simplified.first())
        assertEquals(Coord(139.5200, 35.3000), simplified[1])
        assertEquals(pts.last(), simplified.last())
    }

    @Test
    fun flightCodeDeduplicatesRepeatedCarrierPrefix() {
        val t1 = TransportInfo(airlineCode = "NH", flightNumber = "1731")
        assertEquals("NH 1731", t1.buildFlightCode())

        val t2 = TransportInfo(airlineCode = "NH", flightNumber = "NH1731")
        assertEquals("NH 1731", t2.buildFlightCode())
    }

    @Test
    fun shinkansenCorridorExpandsConnectedCorridorsAndExcludesDisconnectedNishiKyushu() {
        val hokuriku = RailwayRepository.expandedCorridorLines("北陸新幹線")
        assertTrue("北陸新幹線" in hokuriku)
        assertTrue("上越新幹線" in hokuriku)
        assertTrue("東北新幹線" in hokuriku)

        val kyushu = RailwayRepository.expandedCorridorLines("九州新幹線")
        assertTrue("東海道新幹線" in kyushu)
        assertTrue("山陽新幹線" in kyushu)
        assertFalse("西九州新幹線" in kyushu)

        assertEquals("山手線 (JR東日本)", RailwayRepository.formatViaChipLabel("JR東日本::山手線"))
    }

    @Test
    fun cropAspectFlipsPortraitLandscapeAndSupportsFreeAspect() {
        val portrait45 = CropFrameSettings(aspectPreset = CropAspectPreset.RATIO_4_5, isLandscapeOrientation = false)
        assertEquals(0.8f, portrait45.effectiveAspect(1920, 1080), 1e-4f)

        val landscape54 = CropFrameSettings(aspectPreset = CropAspectPreset.RATIO_4_5, isLandscapeOrientation = true)
        assertEquals(1.25f, landscape54.effectiveAspect(1920, 1080), 1e-4f)

        val freeCustom = CropFrameSettings(aspectPreset = CropAspectPreset.FREE, freeAspectRatio = 1.6f)
        assertEquals(1.6f, freeCustom.effectiveAspect(1920, 1080), 1e-4f)
    }

    @Test
    fun coordinateFormattingUsesPreciseDecimalsAndHemispheres() {
        assertEquals("35.65850°N, 139.70220°E", GeoCoordinate.format(35.6585, 139.7022))
        assertEquals("39.23726°N, 123.15003°W", GeoCoordinate.format(39.237255, -123.15003166666666))
        assertEquals("33.86880°S, 151.20930°W", GeoCoordinate.format(-33.8688, -151.2093))
        assertEquals("0.00000°N, 0.00000°E", GeoCoordinate.format(0.0, 0.0))
    }

    @Test
    fun coordinateParserAcceptsDecimalAndDmsForms() {
        assertEquals(35.6585, GeoCoordinate.parseLatitude("35.6585")!!, 1e-9)
        assertEquals(-33.8688, GeoCoordinate.parseLatitude("33°52'7.68\"S")!!, 1e-6)
        assertEquals(35.6585, GeoCoordinate.parseLatitude("N 35.6585")!!, 1e-9)
        assertEquals(139.7022, GeoCoordinate.parseLongitude("E139.7022")!!, 1e-9)
        assertEquals(-123.15003, GeoCoordinate.parseLongitude("123°9'0.11\"W")!!, 1e-5)
        assertEquals(-123.15, GeoCoordinate.parseLongitude("-123.15")!!, 1e-9)
    }

    @Test
    fun coordinateParserRejectsOutOfRangeAndGarbage() {
        assertEquals(null, GeoCoordinate.parseLatitude("91"))
        assertEquals(null, GeoCoordinate.parseLatitude("-90.0001"))
        assertEquals(null, GeoCoordinate.parseLongitude("180.5"))
        assertEquals(null, GeoCoordinate.parseLongitude("not a coordinate"))
        assertEquals(null, GeoCoordinate.parseLatitude(""))
        assertEquals(null, GeoCoordinate.parseLatitude("35.6W"))
        assertEquals(null, GeoCoordinate.parseLongitude("139.7S"))
        assertEquals(null, GeoCoordinate.parseLatitude("35°60'0\"N"))
        assertEquals(null, GeoCoordinate.parseLatitude("35°0'60\"N"))
    }

    @Test
    fun coordinateParserHandlesNegativeDmsDecimalCommaAndBoundaries() {
        assertEquals(-33.8688, GeoCoordinate.parseLatitude("-33°52'7.68\"")!!, 1e-6)
        assertEquals(48.8587, GeoCoordinate.parseLatitude("48,8587")!!, 1e-9)
        assertEquals(90.0, GeoCoordinate.parseLatitude("90")!!, 1e-9)
        assertEquals(-180.0, GeoCoordinate.parseLongitude("180W")!!, 1e-9)
        assertEquals(null, GeoCoordinate.parseLongitude("-180.0001"))
    }

    @Test
    fun spotModeUsesPhotoCoordinateForMapPin() {
        val info = TransportInfo(mode = jp.nico2525.sukashimap.model.TransportMode.SPOT, photoLat = 35.6585, photoLon = 139.7022)
        assertEquals(Coord(139.7022, 35.6585), info.currentGpsCoord())
        assertEquals(null, info.startCoord())
        assertEquals(null, info.endCoord())
    }

    @Test
    fun airlinesSearchFindsAnaAndJalFirst() {
        val nh = AirlinesCatalog.search("NH")
        assertEquals("NH", nh.first().code)
        val ana = AirlinesCatalog.search("ANA")
        assertEquals("NH", ana.first().code)
    }
}

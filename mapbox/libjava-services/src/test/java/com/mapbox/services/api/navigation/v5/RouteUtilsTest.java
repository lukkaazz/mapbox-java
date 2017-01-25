package com.mapbox.services.api.navigation.v5;

import com.google.gson.Gson;
import com.mapbox.services.Constants;
import com.mapbox.services.api.BaseTest;
import com.mapbox.services.api.ServicesException;
import com.mapbox.services.api.directions.v5.models.DirectionsResponse;
import com.mapbox.services.api.directions.v5.models.LegStep;
import com.mapbox.services.api.directions.v5.models.RouteLeg;
import com.mapbox.services.api.utils.turf.TurfConstants;
import com.mapbox.services.api.utils.turf.TurfException;
import com.mapbox.services.api.utils.turf.TurfMeasurement;
import com.mapbox.services.commons.geojson.LineString;
import com.mapbox.services.commons.models.Position;
import com.mapbox.services.commons.utils.PolylineUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteUtilsTest extends BaseTest {

  private static final String DIRECTIONS_V5_FIXTURE = "src/test/fixtures/directions_v5.json";
  private static final String DIRECTIONS_V5_PRECISION_6_FIXTURE = "src/test/fixtures/directions_v5_precision_6.json";

  private DirectionsResponse response;
  private RouteLeg route;

  private DirectionsResponse responsePrecision6;
  private RouteLeg routePrecision6;

  @Before
  public void setUp() throws IOException {
    // Directions V5
    Gson gson = new Gson();
    byte[] content = Files.readAllBytes(Paths.get(DIRECTIONS_V5_FIXTURE));
    String body = new String(content, Charset.forName("utf-8"));
    response = gson.fromJson(body, DirectionsResponse.class);
    route = response.getRoutes().get(0).getLegs().get(0);

    // Directions V5 precision 6
    content = Files.readAllBytes(Paths.get(DIRECTIONS_V5_PRECISION_6_FIXTURE));
    body = new String(content, Charset.forName("utf-8"));
    responsePrecision6 = gson.fromJson(body, DirectionsResponse.class);
    routePrecision6 = responsePrecision6.getRoutes().get(0).getLegs().get(0);
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();


  @Test
  public void isInStepTest() throws ServicesException, TurfException {
    RouteUtils routeUtils = new RouteUtils();

    // For each step, the first coordinate is in the step
    for (int stepIndex = 0; stepIndex < route.getSteps().size(); stepIndex++) {
      LegStep step = route.getSteps().get(stepIndex);
      List<Position> coords = PolylineUtils.decode(step.getGeometry(), Constants.OSRM_PRECISION_V5);
      assertTrue(routeUtils.isInStep(coords.get(0), route, stepIndex));
    }
  }

  @Test
  public void getDistanceToStepTest() throws ServicesException, TurfException {
    RouteUtils routeUtils = new RouteUtils();

    // For each step, the distance to the first coordinate is zero
    for (int stepIndex = 0; stepIndex < route.getSteps().size(); stepIndex++) {
      LegStep step = route.getSteps().get(stepIndex);
      List<Position> coords = PolylineUtils.decode(step.getGeometry(), Constants.OSRM_PRECISION_V5);
      assertEquals(0.0, routeUtils.getDistanceToStep(coords.get(0), route, stepIndex), DELTA);
    }
  }

  @Test
  public void getDistanceToNextStepTest() throws ServicesException, TurfException {
    RouteUtils routeUtils = new RouteUtils();

    // check all steps to see if distance is measured correctly on each one. assert equals compares API value to
    // calculated.
    for (int stepIndex = 0; stepIndex < routePrecision6.getSteps().size() - 1; stepIndex++) {
      LegStep step = routePrecision6.getSteps().get(stepIndex);

      List<Position> coords = PolylineUtils.decode(step.getGeometry(), Constants.OSRM_PRECISION_6_V5);
      double distance = routeUtils.getDistanceToNextStep(coords.get(0), routePrecision6, stepIndex);
      distance = distance * 1000; // Convert distance to meters

      // Delta is 2 meters
      assertEquals(routePrecision6.getSteps().get(stepIndex).getDistance(), distance, 2);
    }
  }

  // TODO add back this test
//  @Test
//  public void getDistanceToEndOfRouteTest() throws TurfException {
//    RouteUtils routeUtils = new RouteUtils();
//
//    List<Position> coords = PolylineUtils.decode(responsePrecision6.getRoutes().get(0).getGeometry(), Constants.OSRM_PRECISION_6_V5);
//    double distance = routeUtils.getDistanceToEndOfRoute(coords.get(0), responsePrecision6.getRoutes().get(0));
//    distance = distance * 1000; // Convert distance to meters
//    assertEquals(responsePrecision6.getRoutes().get(0).getDistance(), distance, 2);
//  }

  @Test
  public void getSnapToRouteTest() throws ServicesException, TurfException {
    RouteUtils routeUtils = new RouteUtils();

    // For each step, the first coordinate snap point is the same point
    for (int stepIndex = 0; stepIndex < route.getSteps().size(); stepIndex++) {
      LegStep step = route.getSteps().get(stepIndex);
      List<Position> coords = PolylineUtils.decode(step.getGeometry(), Constants.OSRM_PRECISION_V5);
      Position snapPoint = routeUtils.getSnapToRoute(coords.get(0), route, stepIndex);
      assertEquals(coords.get(0).getLatitude(), snapPoint.getLatitude(), DELTA);
      assertEquals(coords.get(0).getLongitude(), snapPoint.getLongitude(), DELTA);
    }
  }

  @Test
  public void isOffRouteTest() throws ServicesException, TurfException {
    RouteUtils routeUtils = new RouteUtils();

    // For each step, the first coordinate is not off-route
    for (int stepIndex = 0; stepIndex < route.getSteps().size(); stepIndex++) {
      LegStep step = route.getSteps().get(stepIndex);
      List<Position> coords = PolylineUtils.decode(step.getGeometry(), Constants.OSRM_PRECISION_V5);
      assertFalse(routeUtils.isOffRoute(coords.get(0), route));
    }

    // The route goes from SF (N) to San Jose (S). So 0.1 km south of the last point should
    // be off-route
    LegStep lastStep = route.getSteps().get(route.getSteps().size() - 1);
    List<Position> lastCoords = PolylineUtils.decode(lastStep.getGeometry(), Constants.OSRM_PRECISION_V5);
    Position offRoutePoint = TurfMeasurement.destination(
      lastCoords.get(0), routeUtils.getOffRouteThresholdKm(), 180, TurfConstants.UNIT_DEFAULT);
    assertTrue(routeUtils.isOffRoute(offRoutePoint, route));
  }

  @Test
  public void getClosestStepTest() throws ServicesException, TurfException {
    RouteUtils routeUtils = new RouteUtils();

    // For each step, the first coordinate is closest to its step
    for (int stepIndex = 0; stepIndex < route.getSteps().size(); stepIndex++) {
      LegStep step = route.getSteps().get(stepIndex);
      List<Position> coords = PolylineUtils.decode(step.getGeometry(), Constants.OSRM_PRECISION_V5);
      assertEquals(stepIndex, routeUtils.getClosestStep(coords.get(0), route));
    }
  }
}

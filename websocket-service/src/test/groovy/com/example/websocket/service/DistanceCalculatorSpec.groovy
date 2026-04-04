package com.example.websocket.service

import spock.lang.Specification

class DistanceCalculatorSpec extends Specification {

    def 'SF to LA should be approximately 347 miles'() {
        when:
        def distance = DistanceCalculator.calculateMiles(37.7749, -122.4194, 34.0522, -118.2437)
        then:
        Math.abs(distance - 347.0) < 5.0
    }

    def 'same point should return 0'() {
        when:
        def distance = DistanceCalculator.calculateMiles(37.7749, -122.4194, 37.7749, -122.4194)
        then:
        distance == 0.0d
    }

    def 'antipodal points should be approximately half Earth circumference'() {
        when:
        def distance = DistanceCalculator.calculateMiles(0.0, 0.0, 0.0, 180.0)
        then:
        Math.abs(distance - 12437.0) < 10.0
    }

    def 'two points within 5 mile radius'() {
        when:
        def distance = DistanceCalculator.calculateMiles(37.7749, -122.4194, 37.8049, -122.4194)
        then:
        distance > 0.0
        distance <= 5.0
    }

    def 'NY to London should be approximately 3459 miles'() {
        when:
        def distance = DistanceCalculator.calculateMiles(40.7128, -74.0060, 51.5074, -0.1278)
        then:
        Math.abs(distance - 3459.0) < 10.0
    }
}

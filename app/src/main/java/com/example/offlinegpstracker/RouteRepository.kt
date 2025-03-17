package com.example.offlinegpstracker

import kotlinx.coroutines.flow.Flow

class RouteRepository(
    private val routeDao: RouteDao,
    private val routePointDao: RoutePointDao
) {
    suspend fun insertRoute(route: Route): Long = routeDao.insert(route)

    suspend fun getRoute(routeId: Int): Route? = routeDao.getRoute(routeId)

    suspend fun updateRouteEndTime(routeId: Int, endTime: Long) =
        routeDao.updateRouteEndTime(routeId, endTime)

    suspend fun updateRouteEndTimeAndSnapshot(routeId: Int, endTime: Long, snapshotPath: String?) =
        routeDao.updateRouteEndTimeAndSnapshot(routeId, endTime, snapshotPath)

    suspend fun updateRoute(route: Route) = routeDao.update(route)

    suspend fun updateRouteSnapshot(
        routeId: Int,
        snapshotPath: String,
        centerLat: Double,
        centerLon: Double,
        zoom: Int
    ) {
        val route = getRoute(routeId)?.copy(
            snapshotPath = snapshotPath,
            centerLat = centerLat,
            centerLon = centerLon,
            zoom = zoom
        )
        route?.let { updateRoute(it) }
    }

    suspend fun insertPoint(point: RoutePoint) = routePointDao.insert(point)

    fun getPointsForRoute(routeId: Int): Flow<List<RoutePoint>> =
        routePointDao.getPointsForRoute(routeId)

    fun getAllRoutes(): Flow<List<Route>> = routeDao.getAllRoutes()
}
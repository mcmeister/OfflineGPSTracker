package com.example.offlinegpstracker

import kotlinx.coroutines.flow.Flow

class RouteRepository(
    private val routeDao: RouteDao,
    private val routePointDao: RoutePointDao
) {
    suspend fun insertRoute(route: Route): Long = routeDao.insert(route)
    suspend fun getRoute(routeId: Int): Route? = routeDao.getRoute(routeId)
    suspend fun updateRouteEndTime(routeId: Int, endTime: Long) = routeDao.updateRouteEndTime(routeId, endTime)

    // Added missing method that updates both end time and snapshot
    suspend fun updateRouteEndTimeAndSnapshot(routeId: Int, endTime: Long, snapshotPath: String?) =
        routeDao.updateRouteEndTimeAndSnapshot(routeId, endTime, snapshotPath)

    suspend fun insertPoint(point: RoutePoint) = routePointDao.insert(point)

    fun getPointsForRoute(routeId: Int): Flow<List<RoutePoint>> = routePointDao.getPointsForRoute(routeId)

    // Added missing method to retrieve all routes for saved routes
    fun getAllRoutes(): Flow<List<Route>> = routeDao.getAllRoutes()
}
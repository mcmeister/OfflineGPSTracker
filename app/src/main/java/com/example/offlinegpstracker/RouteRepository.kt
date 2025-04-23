package com.example.offlinegpstracker

import kotlinx.coroutines.flow.Flow

class RouteRepository(
    val routeDao: RouteDao,
    private val routePointDao: RoutePointDao
) {
    suspend fun insertRoute(route: Route): Long = routeDao.insert(route)
    suspend fun getRoute(routeId: Int): Route? = routeDao.getRoute(routeId)
    suspend fun updateRouteEndTime(routeId: Int, endTime: Long) = routeDao.updateRouteEndTime(routeId, endTime)

    suspend fun insertPoint(point: RoutePoint) = routePointDao.insert(point)
    suspend fun getLastUnfinishedRoute(): Route? = routeDao.getLastUnfinishedRoute()

    fun getPointsForRoute(routeId: Int): Flow<List<RoutePoint>> = routePointDao.getPointsForRoute(routeId)

    // Added missing method to retrieve all routes for saved routes
    fun getAllRoutes(): Flow<List<Route>> = routeDao.getAllRoutes()

    suspend fun updateRouteSnapshot(routeId: Int, snapshotPath: String) {
        routeDao.updateRouteSnapshot(routeId, snapshotPath)
    }
}
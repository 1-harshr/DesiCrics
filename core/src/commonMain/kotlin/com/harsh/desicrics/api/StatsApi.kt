package com.harsh.desicrics.api

// Stats.
//
//   GET /players/{id}/stats?season=&format=        → CareerStats (season is a year; format a FormatFilter)
//   GET /stats/compare?a=&b=&season=&format=       → List<CareerStats>, a then b
//   GET /leaderboards?scope=&id=&metric=&season=   → List<LeaderboardRow>, best first
//       scope CITY (id: the city; the caller's own by default), TOURNAMENT (id: the tournament), FRIENDS

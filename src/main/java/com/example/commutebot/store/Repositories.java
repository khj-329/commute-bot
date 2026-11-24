package com.example.commutebot.store;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface BusStopRepository extends JpaRepository<BusStop, Long> {

    Optional<BusStop> findFirstByRepresentativeTrue();

    List<BusStop> findByRepresentativeFalseOrderByIdAsc();

    void deleteByRepresentativeTrue();

    void deleteByRepresentativeFalseAndArsId(String arsId);

    void deleteByRepresentativeFalseAndArsIdAndStId(String arsId, String stId);
}

interface SubwayStationRepository extends JpaRepository<SubwayStation, Long> {

    boolean existsByNameAndLine(String name, String line);

    void deleteByNameAndLine(String name, String line);

    List<SubwayStation> findAllByOrderByIdAsc();
}

interface AlertSettingRepository extends JpaRepository<AlertSetting, Long> {
}

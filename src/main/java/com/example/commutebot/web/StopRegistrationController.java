package com.example.commutebot.web;

import com.example.commutebot.bus.BusStopCandidate;
import com.example.commutebot.bus.BusStopLookup;
import com.example.commutebot.common.ApiCallException;
import com.example.commutebot.store.UserSettingsService;
import com.example.commutebot.subway.SubwayStationCatalog;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 정류장·역 등록과 삭제. 결과 안내는 flash 속성 message / error 로 대시보드에 보여준다. */
@Controller
public class StopRegistrationController {

    static final String PENDING = "pendingBusChoice";
    private static final Pattern ARS_ID = Pattern.compile("\\d{5}");

    /** 후보 선택 대기 상태. */
    record PendingChoice(String arsId, boolean representative, List<BusStopCandidate> candidates)
            implements Serializable {
    }

    private final BusStopLookup lookup;
    private final UserSettingsService settings;
    private final SubwayStationCatalog catalog;

    public StopRegistrationController(BusStopLookup lookup, UserSettingsService settings,
            SubwayStationCatalog catalog) {
        this.lookup = lookup;
        this.settings = settings;
        this.catalog = catalog;
    }

    @PostMapping("/stops/bus")
    public String registerBus(@RequestParam String arsId,
            @RequestParam(defaultValue = "false") boolean representative,
            HttpSession session, RedirectAttributes redirect) {
        String ars = arsId.trim();
        if (!ARS_ID.matcher(ars).matches()) {
            redirect.addFlashAttribute("error", "표지판 번호는 숫자 5자리입니다: " + arsId);
            return "redirect:/";
        }
        List<BusStopCandidate> candidates;
        try {
            candidates = lookup.findByArsId(ars);
        } catch (ApiCallException e) {
            redirect.addFlashAttribute("error", "정류장 조회 실패: " + e.getMessage());
            return "redirect:/";
        }
        if (candidates.isEmpty()) {
            redirect.addFlashAttribute("error",
                    "존재하지 않는 표지판 번호입니다: " + ars + " (현재 서울 정류장만 지원합니다)");
            return "redirect:/";
        }
        if (candidates.size() == 1) {
            save(candidates.get(0), ars, representative, redirect);
            return "redirect:/";
        }
        session.setAttribute(PENDING, new PendingChoice(ars, representative, List.copyOf(candidates)));
        return "redirect:/stops/bus/choose";
    }

    @GetMapping("/stops/bus/choose")
    public String chooseForm(HttpSession session, Model model) {
        PendingChoice pending = (PendingChoice) session.getAttribute(PENDING);
        if (pending == null) {
            return "redirect:/";
        }
        model.addAttribute("pending", pending);
        return "choose";
    }

    @PostMapping("/stops/bus/choose")
    public String choose(@RequestParam String stId, HttpSession session, RedirectAttributes redirect) {
        PendingChoice pending = (PendingChoice) session.getAttribute(PENDING);
        if (pending == null) {
            redirect.addFlashAttribute("error", "선택할 정류장 후보가 없습니다. 표지판 번호를 다시 입력해 주세요.");
            return "redirect:/";
        }
        Optional<BusStopCandidate> chosen = pending.candidates().stream()
                .filter(c -> c.stId().equals(stId))
                .findFirst();
        if (chosen.isEmpty()) {
            redirect.addFlashAttribute("error", "후보 목록에 없는 정류장입니다. 목록에서 다시 골라 주세요.");
            return "redirect:/stops/bus/choose";
        }
        session.removeAttribute(PENDING);
        save(chosen.get(), pending.arsId(), pending.representative(), redirect);
        return "redirect:/";
    }

    @PostMapping("/stops/subway")
    public String registerSubway(@RequestParam String name, @RequestParam String line,
            RedirectAttributes redirect) {
        String stationName = name.trim();
        String lineName = line.trim();
        if (stationName.isEmpty() || lineName.isEmpty()) {
            redirect.addFlashAttribute("error", "역 이름과 호선을 함께 입력해 주세요.");
            return "redirect:/";
        }
        if (!catalog.exists(stationName, lineName)) {
            List<String> suggestions = catalog.search(stationName);
            String hint = suggestions.isEmpty() ? "비슷한 역이 없습니다." : "후보: " + String.join(", ", suggestions);
            redirect.addFlashAttribute("error",
                    "역 목록에 없는 역입니다: " + stationName + " " + lineName
                            + " (역명과 호선이 정확히 일치해야 합니다). " + hint);
            return "redirect:/";
        }
        settings.addStation(stationName, lineName);
        redirect.addFlashAttribute("message", stationName + " " + lineName + " 역을 등록했습니다.");
        return "redirect:/";
    }

    @PostMapping("/stops/delete")
    public String delete(@RequestParam String type,
            @RequestParam(required = false) String arsId,
            @RequestParam(required = false) String stId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String line,
            RedirectAttributes redirect) {
        switch (type) {
            case "representative" -> settings.clearRepresentativeStop();
            case "transfer" -> {
                if (arsId != null && stId != null && !stId.isBlank()) {
                    settings.removeTransferStop(arsId, stId);
                } else if (arsId != null) {
                    settings.removeTransferStop(arsId);
                }
            }
            case "subway" -> {
                if (name != null && line != null) {
                    settings.removeStation(name, line);
                }
            }
            default -> {
                redirect.addFlashAttribute("error", "알 수 없는 삭제 요청입니다.");
                return "redirect:/";
            }
        }
        redirect.addFlashAttribute("message", "삭제했습니다.");
        return "redirect:/";
    }

    private void save(BusStopCandidate c, String arsId, boolean representative, RedirectAttributes redirect) {
        if (representative) {
            settings.setRepresentativeStop(arsId, c.stId(), c.stName(), c.routeNames());
        } else {
            settings.addTransferStop(arsId, c.stId(), c.stName(), c.routeNames());
        }
        redirect.addFlashAttribute("message", String.format("%s 정류장 %s (%s)을 등록했습니다. 경유 노선: %s",
                representative ? "대표" : "환승", c.stName(), arsId, String.join(", ", c.routeNames())));
    }
}

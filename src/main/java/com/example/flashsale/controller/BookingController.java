package com.example.flashsale.controller;

import com.example.flashsale.service.BookingService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private final BookingService service;

    public BookingController(BookingService service) {
        this.service = service;
    }

    @PostMapping("/book")
    public BookResponse book() {
        return new BookResponse(service.book());
    }

    @GetMapping("/stats")
    public BookingService.Stats stats() {
        return service.stats();
    }

    @PostMapping("/admin/reset")
    public BookingService.Stats reset(@RequestParam(defaultValue = "100") int stock) {
        service.reset(stock);
        return service.stats();
    }

    public record BookResponse(boolean booked) {
    }
}

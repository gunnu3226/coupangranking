package gunnu.coupang.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {

    @GetMapping(value = {
            "/",
            "/parse",
            "/today-result",
            "/saved-data",
            "/product-management",
            "/favorite-order-edit"
    })
    public String app() {
        return "forward:/index.html";
    }
}

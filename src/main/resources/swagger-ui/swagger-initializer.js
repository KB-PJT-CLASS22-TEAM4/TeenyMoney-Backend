// Swagger UI 화면이 열릴 때, 어떤 OpenAPI 문서를 읽어서 어떤 방식으로
// 화면에 보여줄지 설정하는 초기화 코드

window.onload = function () {
    window.ui = SwaggerUIBundle({
        url: "/v2/api-docs",
        dom_id: "#swagger-ui",
        deepLinking: true,
        presets: [
            SwaggerUIBundle.presets.apis,
            SwaggerUIStandalonePreset
        ],
        plugins: [
            SwaggerUIBundle.plugins.DownloadUrl
        ],
        layout: "StandaloneLayout"
    });
};

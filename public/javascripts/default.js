document.addEventListener('DOMContentLoaded', function () {
    $(function () {
        $('[data-toggle="tooltip"]').tooltip()
    });

}, false);

function toggleLoader(e) {
    $('#loader').prop("hidden", false);
}
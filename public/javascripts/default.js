document.addEventListener('DOMContentLoaded', function () {
    $(function () {
        $('[data-toggle="tooltip"]').tooltip()
    });

}, false);

function toggleLoader(e) {
    $('#loader').prop("hidden", false);
}

function approveProp(id) {
    document.getElementById('modalTitle').innerHTML = "Approval";
    document.getElementById('modalBody').innerHTML = "Are you sure you want to approve this proposal?";
    document.getElementById('modalOkBtn').addEventListener('click', function () {
        $('#approveLoader').prop("hidden", false);
        $('#approveBtn').prop("disabled", true);
        $('#rejectBtn').prop("disabled", true);
        $("#propModal").modal("toggle")
        // tell server about it and toggle loader and enable btn!
    });
    $("#propModal").modal("show")
}

function rejectProp(id) {
    document.getElementById('modalTitle').innerHTML = "Rejection";
    document.getElementById('modalBody').innerHTML = "Are you sure you want to reject this proposal?";
    document.getElementById('modalOkBtn').addEventListener('click', function () {
        $('#rejectLoader').prop("hidden", false);
        $('#rejectBtn').prop("disabled", true);
        $('#approveBtn').prop("disabled", true);
        $("#propModal").modal("toggle")
        // tell server about it and toggle loader and enable btn!
    });
    $("#propModal").modal("show")
}

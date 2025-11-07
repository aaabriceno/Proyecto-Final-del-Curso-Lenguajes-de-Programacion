document.addEventListener('DOMContentLoaded', function() {
    var form = document.querySelector('form');

    // [IDF-0001] : envio de datos para el inicio de sesión.
    form.addEventListener('submit', function(event) {
        event.preventDefault();

        var name = document.getElementById('name').value;
        var password = document.getElementById('password').value;

        var requestData = {
            name: name,
            password: password
        };
        
        fetch('/signin', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestData)
        })
        .then(response => response.json())
        .then(data => {
            if (data.success)
                window.location.href = data.url;
            else{
                document.getElementById('name').value = '';
                document.getElementById('password').value = '';
                alert(data.message);
            }
        })
        .catch(error => console.error('Error:', error));
        
      
    });

});

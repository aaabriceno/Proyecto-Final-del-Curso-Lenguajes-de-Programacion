// [IDF-0026] Genera una barra de navegacion para un usuario sin cuenta.
function generateNavbar() {
    var header = document.createElement('header');
    var stylevar = document.createElement('link');
    stylevar.href = "/styles/navbar.css";
    stylevar.rel = "stylesheet"; 
    stylevar.type = "text/css";

    var nav = document.createElement('nav');
    var ul = document.createElement('ul');

    var liYouEz = document.createElement('li');
    var aYouEz = document.createElement('a');
    aYouEz.href = '/';
    aYouEz.textContent = 'DownEz';
    liYouEz.appendChild(aYouEz);
    ul.appendChild(liYouEz);
    
    var options = {"Register":"register.html", "Sign in":"login.html"};
    for(var key in options){
        var liInformes = document.createElement('li');
        var aInformes = document.createElement('a');
        aInformes.href = options[key];
        aInformes.textContent = key;    
        liInformes.appendChild(aInformes);
        ul.appendChild(liInformes);
    };

    nav.appendChild(ul);
    header.appendChild(nav);
    header.appendChild(stylevar);

    document.body.insertBefore(header, document.body.firstChild);
}

document.addEventListener('DOMContentLoaded', function () {
    generateNavbar();
});

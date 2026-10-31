const firebaseConfig = {
    apiKey: "AIzaSyAGSes0nNoGWzgweoYrta99oRU_fKxkZCc",
    authDomain: "mess-manager-a91c8.firebaseapp.com",
    databaseURL: "https://mess-manager-a91c8-default-rtdb.firebaseio.com",
    projectId: "mess-manager-a91c8",
    storageBucket: "mess-manager-a91c8.firebasestorage.app",
    messagingSenderId: "714155755588",
    appId: "1:714155755588:web:83b0064016bf657a789411",
    measurementId: "G-YRZQHP98SF"
};

firebase.initializeApp(firebaseConfig);
const auth = firebase.auth();
const db = firebase.database();
